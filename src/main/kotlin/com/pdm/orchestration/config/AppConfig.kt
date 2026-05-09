package com.pdm.orchestration.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@ConfigurationProperties(prefix = "pdm")
data class PdmProperties(val baseUrl: String, val apiKey: String)

@ConfigurationProperties(prefix = "upload")
data class UploadProperties(val baseUrl: String, val apiKey: String)

@ConfigurationProperties(prefix = "orchestration")
data class OrchestrationProperties(val maxConcurrency: Int = 100)

@Configuration
@EnableConfigurationProperties(PdmProperties::class, UploadProperties::class, OrchestrationProperties::class)
class AppConfig {

    @Bean
    fun pdmRestClient(props: PdmProperties): RestClient =
        RestClient.builder()
            .baseUrl(props.baseUrl)
            .defaultHeader("X-Api-Key", props.apiKey)
            .requestFactory(requestFactory(readTimeoutMs = 60000))
            .build()

    @Bean
    fun uploadRestClient(props: UploadProperties): RestClient =
        RestClient.builder()
            .baseUrl(props.baseUrl)
            .defaultHeader("X-Api-Key", props.apiKey)
            .requestFactory(requestFactory(readTimeoutMs = 10000))
            .build()

    @Bean
    fun s3RestClient(): RestClient =
        RestClient.builder()
            .requestFactory(requestFactory(readTimeoutMs = 120000))
            .build()

    private fun requestFactory(connectTimeoutMs: Long = 5000, readTimeoutMs: Long): JdkClientHttpRequestFactory {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .build()
        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofMillis(readTimeoutMs))
        }
    }
}
