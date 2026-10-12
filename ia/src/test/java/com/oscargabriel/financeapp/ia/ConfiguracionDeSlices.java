package com.oscargabriel.financeapp.ia;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Los @WebFluxTest buscan una @SpringBootConfiguration subiendo por los paquetes, y el main vive en
 * app. Escanea desde la raiz y no desde ia para que el slice encuentre el manejador de errores y los
 * filtros de compartido.
 */
@SpringBootApplication(scanBasePackages = "com.oscargabriel.financeapp")
class ConfiguracionDeSlices {
}
