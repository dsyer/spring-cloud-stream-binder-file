A Spring Cloud Stream binder for a plain test files. Can be used to send and receive messages over named pipes.

## Usage

Add this jar to the classpath of a Spring Cloud Stream application as the only binder implementation. Endpoints are exposed at `target/stream/{name}` where `{name}` is the destination name (e.g. `Processor.INPUT` and `Processor.OUTPUT`).

Configuration properties (in addition to the ones provided by Spring Cloud Stream for bindings and channel names, etc.):

| Key                            | Default | Description                |
|--------------------------------|---------|----------------------------|
| `spring.cloud.stream.binder.file.prefix`         | `target/stream` | The prefix for the file paths |
