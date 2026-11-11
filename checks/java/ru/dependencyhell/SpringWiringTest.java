package ru.dependencyhell;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.dependencyhell.client.RegistryClient;
import ru.dependencyhell.repository.RunRepository;
import ru.dependencyhell.service.ConfigurationService;
import ru.dependencyhell.service.DependencyService;

class SpringWiringTest {
    @Test void wiresControllerServicesRepositoryAndRegistryClient() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(new DriverManagerDataSource("jdbc:fake:unused")));
            context.register(RunRepository.class, RegistryClient.class, ConfigurationService.class, DependencyService.class, ConfigurationController.class);
            context.refresh();
            assertNotNull(context.getBean(ConfigurationController.class));
            assertNotNull(context.getBean(DependencyService.class));
            assertNotNull(context.getBean(RunRepository.class));
            assertNotNull(context.getBean(RegistryClient.class));
        }
    }
}
