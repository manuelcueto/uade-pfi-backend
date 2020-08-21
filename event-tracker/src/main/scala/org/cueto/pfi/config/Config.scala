package org.cueto.pfi.config

final case class Config(kafka: KafkaConfig, api: ApiConfig, database: DatabaseConfig)

final case class DatabaseConfig(host: String, database: String, username: String, password: String, threadPoolSize: Int)

final case class KafkaConfig(bootstrapServer: String, consumerGroup: String)

final case class ApiConfig(host: String, port: Int)
