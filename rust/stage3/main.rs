use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashMap, HashSet, VecDeque};
use std::io::Read;
use std::time::Duration;
use tiny_http::{Header, Method, Response, Server, StatusCode};

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct Requirement {
    name: String,
    version_spec: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct Package {
    name: String,
    version: Option<String>,
    dependencies: Vec<Requirement>,
}

impl Package {
    fn id(&self) -> String {
        match &self.version {
            Some(v) => format!("{}@{}", self.name, v),
            None => self.name.clone(),
        }
    }
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct Node {
    id: String,
    name: String,
    version: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
struct Edge {
    from: String,
    to: String,
    cycle: bool,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct Graph {
    root: String,
    nodes: Vec<Node>,
    edges: Vec<Edge>,
    visit_order: Vec<String>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct LoadOrder {
    possible: bool,
    packages: Vec<String>,
    cycle_edges: Vec<Edge>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct GraphRequest {
    root: String,
    test_mode: bool,
    test_graph: Option<BTreeMap<String, Vec<String>>>,
    repository: Option<String>,
}

struct Frame {
    package: Package,
    next: usize,
}

// DFS использует явный стек кадров, а не рекурсивные вызовы.
fn build_graph<F>(root: Package, mut resolve: F) -> Result<Graph, String>
where
    F: FnMut(&Requirement) -> Result<Package, String>,
{
    let root_id = root.id();
    let mut graph = Graph { root: root_id.clone(), nodes: Vec::new(), edges: Vec::new(), visit_order: Vec::new() };
    graph.nodes.push(Node { id: root_id.clone(), name: root.name.clone(), version: root.version.clone() });
    graph.visit_order.push(root_id.clone());
    let mut states = HashMap::new();
    states.insert(root_id, 1u8);
    let mut seen_edges: HashSet<(String, String)> = HashSet::new();
    let mut stack = vec![Frame { package: root, next: 0 }];
    while !stack.is_empty() {
        let step = {
            let frame = stack.last_mut().unwrap();
            if frame.next < frame.package.dependencies.len() {
                let item = frame.package.dependencies[frame.next].clone();
                frame.next += 1;
                Some((frame.package.id(), item))
            } else {
                None
            }
        };
        if let Some((parent, requirement)) = step {
            let child = resolve(&requirement)?;
            let id = child.id();
            let state = states.get(&id).copied().unwrap_or(0);
            if seen_edges.insert((parent.clone(), id.clone())) {
                if graph.edges.len() >= 50000 { return Err("Граф превышает лимит 50000 связей".to_string()); }
                graph.edges.push(Edge { from: parent, to: id.clone(), cycle: state == 1 });
            }
            if state == 0 {
                if graph.nodes.len() >= 10000 { return Err("Граф превышает лимит 10000 пакетов".to_string()); }
                states.insert(id.clone(), 1);
                graph.visit_order.push(id.clone());
                graph.nodes.push(Node { id, name: child.name.clone(), version: child.version.clone() });
                stack.push(Frame { package: child, next: 0 });
            }
        } else {
            let finished = stack.pop().unwrap();
            states.insert(finished.package.id(), 2);
        }
    }
    Ok(graph)
}

fn is_test_name(value: &str) -> bool {
    !value.is_empty() && value.len() <= 214 && value.bytes().all(|c| c.is_ascii_uppercase())
}

fn test_package(name: &str, map: &BTreeMap<String, Vec<String>>) -> Package {
    let dependencies = map.get(name).cloned().unwrap_or_default().into_iter()
        .map(|item| Requirement { name: item, version_spec: String::new() }).collect();
    Package { name: name.to_string(), version: None, dependencies }
}

fn graph_from_test(root: &str, map: &BTreeMap<String, Vec<String>>) -> Result<Graph, String> {
    if !is_test_name(root) { return Err("Имя тестового пакета должно состоять из больших латинских букв".into()); }
    if !map.contains_key(root) && !map.values().any(|deps| deps.iter().any(|name| name == root)) {
        return Err(format!("Пакет {} отсутствует в тестовом репозитории", root));
    }
    if map.len() > 10000 { return Err("Тестовый репозиторий превышает лимит 10000 пакетов".into()); }
    for (name, deps) in map {
        if !is_test_name(name) || deps.iter().any(|dep| !is_test_name(dep)) {
            return Err("Имена тестовых пакетов должны состоять из больших латинских букв".into());
        }
    }
    build_graph(test_package(root, map), |requirement| Ok(test_package(&requirement.name, map)))
}

// Ребро from -> to означает: сначала нужна зависимость to, затем пакет from.
fn calculate_load_order(graph: &Graph) -> Result<LoadOrder, String> {
    if graph.nodes.is_empty() || graph.nodes.len() > 10000 || graph.edges.len() > 50000 {
        return Err("Некорректный размер графа".into());
    }
    let mut ids: HashMap<&str, usize> = HashMap::new();
    for (index, node) in graph.nodes.iter().enumerate() {
        if node.id.is_empty() || ids.insert(&node.id, index).is_some() {
            return Err("В графе есть пустая или повторяющаяся вершина".into());
        }
    }
    if !ids.contains_key(graph.root.as_str()) { return Err("Корень отсутствует в графе".into()); }
    let mut pending = vec![0usize; graph.nodes.len()];
    let mut dependents = vec![Vec::<usize>::new(); graph.nodes.len()];
    let mut seen = HashSet::new();
    for edge in &graph.edges {
        let parent = *ids.get(edge.from.as_str()).ok_or("Неизвестная вершина ребра")?;
        let dependency = *ids.get(edge.to.as_str()).ok_or("Неизвестная зависимость ребра")?;
        if !seen.insert((parent, dependency)) { return Err("Повторяющееся ребро графа".into()); }
        pending[parent] += 1;
        dependents[dependency].push(parent);
    }
    let mut ready: VecDeque<usize> = pending.iter().enumerate()
        .filter_map(|(index, count)| if *count == 0 { Some(index) } else { None }).collect();
    let mut order = Vec::with_capacity(graph.nodes.len());
    while let Some(dependency) = ready.pop_front() {
        order.push(graph.nodes[dependency].id.clone());
        for &parent in &dependents[dependency] {
            pending[parent] -= 1;
            if pending[parent] == 0 { ready.push_back(parent); }
        }
    }
    let cycle_edges: Vec<Edge> = graph.edges.iter().filter(|edge| edge.cycle).cloned().collect();
    if order.len() != graph.nodes.len() {
        if cycle_edges.is_empty() { return Err("Цикл не отмечен обратным ребром DFS".into()); }
        return Ok(LoadOrder { possible: false, packages: Vec::new(), cycle_edges });
    }
    if !cycle_edges.is_empty() { return Err("Ациклический граф ошибочно содержит отметку цикла".into()); }
    Ok(LoadOrder { possible: true, packages: order, cycle_edges: Vec::new() })
}

fn resolve_remote(agent: &ureq::Agent, api: &str, repository: &str, name: &str, spec: &str) -> Result<Package, String> {
    let address = format!("{}/api/internal/resolve", api.trim_end_matches('/'));
    let response = agent.post(&address).send_json(serde_json::json!({"name": name, "versionSpec": spec, "repository": repository}));
    let response = match response {
        Ok(value) => value,
        Err(ureq::Error::Status(status, response)) => return Err(format!("Ошибка API HTTP {}: {}", status, response.into_string().unwrap_or_default())),
        Err(error) => return Err(format!("Не удалось получить {}: {}", name, error)),
    };
    let package: Package = response.into_json().map_err(|error| format!("Некорректный ответ API: {}", error))?;
    if package.name.is_empty() || package.version.as_ref().map_or(true, |v| v.is_empty()) {
        return Err("API вернул пакет без имени или версии".into());
    }
    Ok(package)
}

fn process(input: GraphRequest, api: &str) -> Result<Graph, String> {
    if input.test_mode {
        return graph_from_test(&input.root, input.test_graph.as_ref().ok_or("Тестовый граф не передан")?);
    }
    let repo = input.repository.ok_or("URL реестра не передан")?;
    let agent = ureq::AgentBuilder::new().timeout_connect(Duration::from_secs(5)).timeout_read(Duration::from_secs(40)).timeout_write(Duration::from_secs(10)).build();
    let mut cache: HashMap<(String, String), Package> = HashMap::new();
    let root = resolve_remote(&agent, api, &repo, &input.root, "latest")?;
    cache.insert((input.root.clone(), "latest".to_string()), root.clone());
    build_graph(root, |requirement| {
        let key = (requirement.name.clone(), requirement.version_spec.clone());
        if let Some(value) = cache.get(&key) { return Ok(value.clone()); }
        let value = resolve_remote(&agent, api, &repo, &requirement.name, &requirement.version_spec)?;
        cache.insert(key, value.clone());
        Ok(value)
    })
}

fn handle(mut request: tiny_http::Request, api: &str) {
    let is_health = request.method() == &Method::Get && request.url() == "/health";
    let is_graph = request.method() == &Method::Post && request.url() == "/api/graph";
    let is_order = request.method() == &Method::Post && request.url() == "/api/order";
    let (status, body) = if is_health {
        (200, serde_json::json!({"status":"UP"}))
    } else if is_graph || is_order {
        let mut bytes = Vec::new();
        let limit = if is_order { 64 * 1024 * 1024 } else { 10 * 1024 * 1024 };
        let read = request.as_reader().take((limit + 1) as u64).read_to_end(&mut bytes);
        if read.is_err() || bytes.len() > limit {
            (400, serde_json::json!({"error":format!("Не удалось прочитать запрос или он больше {} МиБ", limit / 1024 / 1024)}))
        } else if is_order {
            match serde_json::from_slice::<Graph>(&bytes) {
                Err(error) => (400, serde_json::json!({"error": format!("Некорректный граф: {}", error)})),
                Ok(graph) => match calculate_load_order(&graph) {
                    Ok(order) => (200, serde_json::to_value(order).unwrap()),
                    Err(error) => (400, serde_json::json!({"error": error})),
                },
            }
        } else {
            match serde_json::from_slice::<GraphRequest>(&bytes) {
                Err(error) => (400, serde_json::json!({"error": format!("Некорректный JSON: {}", error)})),
                Ok(input) => { let test_mode = input.test_mode; match process(input, api) {
                    Ok(graph) => (200, serde_json::to_value(graph).unwrap()),
                    Err(error) => (if test_mode {400} else {502}, serde_json::json!({"error":error})),
                }},
            }
        }
    } else {
        (404, serde_json::json!({"error":"Маршрут не найден"}))
    };
    let header = Header::from_bytes(&b"Content-Type"[..], &b"application/json; charset=utf-8"[..]).unwrap();
    let response = Response::from_string(body.to_string()).with_status_code(StatusCode(status)).with_header(header);
    let _ = request.respond(response);
}

fn main() {
    if std::env::args().nth(1).as_deref() == Some("--healthcheck") {
        if ureq::get("http://127.0.0.1:8082/health").call().is_err() { std::process::exit(1); }
        return;
    }
    let api = std::env::var("SPRING_INTERNAL_URL").unwrap_or_else(|_| "http://api:8080".to_string());
    let server = Server::http("0.0.0.0:8082").expect("Не удалось запустить Rust-сервис");
    println!("Rust graph service listening on :8082");
    for request in server.incoming_requests() {
        let api = api.clone();
        std::thread::spawn(move || handle(request, &api));
    }
}

#[cfg(test)]
mod checks {
    use super::*;
    fn graph(pairs: &[(&str, &[&str])]) -> BTreeMap<String, Vec<String>> {
        pairs.iter().map(|(name, deps)| (name.to_string(), deps.iter().map(|v| v.to_string()).collect())).collect()
    }
    #[test]
    fn traversal_is_depth_first_and_shared_nodes_are_not_cycles() {
        let data = graph(&[("A", &["B", "C"]), ("B", &["D"]), ("C", &["D"]), ("D", &[])]);
        let result = graph_from_test("A", &data).unwrap();
        assert_eq!(result.visit_order, vec!["A", "B", "D", "C"]);
        assert_eq!(result.edges.len(), 4);
        assert!(result.edges.iter().all(|e| !e.cycle));
    }
    #[test]
    fn cycle_and_self_loop_are_marked() {
        let data = graph(&[("A", &["B"]), ("B", &["A", "B"])]);
        let result = graph_from_test("A", &data).unwrap();
        assert_eq!(result.nodes.len(), 2);
        assert_eq!(result.edges.iter().filter(|e| e.cycle).count(), 2);
    }
    #[test]
    fn validates_input_and_handles_missing_leaf() {
        let data = graph(&[("A", &["B"])]);
        assert_eq!(graph_from_test("A", &data).unwrap().visit_order, vec!["A", "B"]);
        assert!(graph_from_test("X", &data).is_err());
        assert!(graph_from_test("a", &data).is_err());
    }
    #[test]
    fn long_chain_does_not_need_recursion() {
        let mut data = BTreeMap::new();
        fn label(mut number: usize) -> String {
            number += 1;
            let mut chars = Vec::new();
            while number > 0 { number -= 1; chars.push((b'A' + (number % 26) as u8) as char); number /= 26; }
            chars.into_iter().rev().collect()
        }
        let names: Vec<String> = (0..1500).map(label).collect();
        for pair in names.windows(2) { data.insert(pair[0].clone(), vec![pair[1].clone()]); }
        data.insert(names.last().unwrap().clone(), vec![]);
        let result = graph_from_test(&names[0], &data).unwrap();
        assert_eq!(result.nodes.len(), names.len());
        let order = calculate_load_order(&result).unwrap();
        assert_eq!(order.packages.first(), names.last());
        assert_eq!(order.packages.last(), names.first());
    }

    #[test]
    fn load_order_puts_dependencies_first_and_handles_shared_nodes() {
        let data = graph(&[("A", &["B", "C"]), ("B", &["D"]), ("C", &["D"]), ("D", &[])]);
        let result = calculate_load_order(&graph_from_test("A", &data).unwrap()).unwrap();
        assert!(result.possible);
        assert_eq!(result.packages, vec!["D", "B", "C", "A"]);
        assert!(result.cycle_edges.is_empty());
    }

    #[test]
    fn load_order_rejects_cycles_including_self_loops() {
        for data in [graph(&[("A", &["B"]), ("B", &["A"])]), graph(&[("A", &["A"])])] {
            let result = calculate_load_order(&graph_from_test("A", &data).unwrap()).unwrap();
            assert!(!result.possible);
            assert!(result.packages.is_empty());
            assert_eq!(result.cycle_edges.len(), 1);
        }
    }

    #[test]
    fn load_order_supports_a_single_package_and_validates_edges() {
        let mut one = graph_from_test("A", &graph(&[("A", &[])])).unwrap();
        assert_eq!(calculate_load_order(&one).unwrap().packages, vec!["A"]);
        one.edges.push(Edge { from: "A".into(), to: "MISSING".into(), cycle: false });
        assert!(calculate_load_order(&one).is_err());
    }
}
