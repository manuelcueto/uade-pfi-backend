package org.cueto.pfi.config

final case class Config(database: DatabaseConfig, server: ServerConfig, kafka: KafkaConfig, email: EmailConfig)

final case class DatabaseConfig(host: String, database: String, username: String, password: String, threadPoolSize: Int)

final case class ServerConfig(bindUrl: String, bindPort: Int, staticFileThreadSize: Int)

final case class KafkaConfig(bootstrapServer: String)

final case class EmailConfig(host: String, port: Int, password: String, sender: String)
