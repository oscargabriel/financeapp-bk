// LOCATION: <BASE_PACKAGE>.infrastructure.config
// Requerido en Spring Boot 4: WebProperties.Resources no se registra como bean automáticamente.
// AbstractErrorWebExceptionHandler lo recibe por inyección — sin este @Bean Spring lanza
// NoSuchBeanDefinitionException al arrancar.
package <BASE_PACKAGE>.infrastructure.config;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResourcesWebPropertiesConfig {

    @Bean
    public WebProperties.Resources resources() {
        return new WebProperties.Resources();
    }
}
