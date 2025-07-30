package com.solvesall.ktor_template.api

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry


internal object Env {
    internal val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val API_PORT = System.getenv("API_PORT")!!.toInt()

}
