package ru.dependencyhell;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.dependencyhell.client.RegistryClient;
import ru.dependencyhell.client.GraphClient;
import ru.dependencyhell.client.VersionClient;
import ru.dependencyhell.repository.GraphRepository;
import ru.dependencyhell.repository.LoadOrderRepository;
import ru.dependencyhell.repository.RunRepository;
import ru.dependencyhell.service.ConfigurationService;
import ru.dependencyhell.service.DependencyService;
import ru.dependencyhell.service.GraphService;
import ru.dependencyhell.service.GraphStorageService;
import ru.dependencyhell.service.ResolutionService;
import ru.dependencyhell.service.LoadOrderService;
import ru.dependencyhell.service.LoadOrderStorageService;

class SpringWiringTest {
    @Test void wiresControllerServicesRepositoryAndRegistryClient() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(new DriverManagerDataSource("jdbc:fake:unused")));
            context.register(RunRepository.class, GraphRepository.class, LoadOrderRepository.class, RegistryClient.class, GraphClient.class, VersionClient.class, ConfigurationService.class, DependencyService.class, GraphStorageService.class, GraphService.class, LoadOrderStorageService.class, LoadOrderService.class, ResolutionService.class, ConfigurationController.class, GraphController.class, LoadOrderController.class, InternalResolutionController.class);
            context.refresh();
            assertNotNull(context.getBean(ConfigurationController.class));
            assertNotNull(context.getBean(DependencyService.class));
            assertNotNull(context.getBean(RunRepository.class));
            assertNotNull(context.getBean(RegistryClient.class));
            assertNotNull(context.getBean(GraphController.class));
            assertNotNull(context.getBean(LoadOrderController.class));
            assertNotNull(context.getBean(LoadOrderService.class));
            assertNotNull(context.getBean(ResolutionService.class));
        }
    }
}
