A Spring Cloud Stream binder for a plain test files. Can be used to send and receive messages over named pipes.

## Usage

Add this jar to the classpath of a Spring Cloud Stream application as the only binder implementation. Endpoints are exposed at `target/stream/{name}` where `{name}` is the destination name (e.g. `Processor.INPUT` and `Processor.OUTPUT`).

Configuration properties (in addition to the ones provided by Spring Cloud Stream for bindings and channel names, etc.):

| Key                            | Default | Description                |
|--------------------------------|---------|----------------------------|
| `spring.cloud.stream.binder.file.prefix`         | `target/stream` | The prefix for the file paths |

## Message Encoding

A message that does not contain line endings can just be appended to the file, e.g. 

```
hello
world
```

is 2 messages with payloads "hello" and "world". A message payload that contains a line ending has to be enclosed in markers:

```
#payload
hello
world
#end
```

is a single message with payload "hello\nworld". Trailing line endings are always removed. Headers can be added by prefixing `#headers` and `name=value` pairs before the payload:

```
#headers
foo=bar
#payload
hello
#end
```

Messages are encoded using `toString()` so it only works if the payload is convertible to a `String`. Spring Cloud Stream already does this if the content types are configured as a stringy media type (e.g. `spring.cloud.bindings.*.contentType=application/json`). If the content types are not configured they default to `null`, in which case only messages with actual `String` payloads will work.

## Building

```
$ ./mvnw clean install
```

The tests run with regular files by default in `target/streams/{input,output}`, deleting them before each test class (so you can inspect the contents after running a test).

There is one test that will only run if some named pipes already exist, so optionally and for a more thorough test do this as well (note the different file location to the other tests):

```
$ mkfifo target/stream/input
$ mkfifo target/stream/output
$ ./mvnw clean install
```
