# Java HTTP/HTTPS Proxy Server

A basic HTTP/HTTPS proxy server built from scratch in Java using
Socket programming, multithreading, and Java I/O.

## Features

- HTTP request forwarding
- HTTPS CONNECT tunneling
- Multithreaded client handling
- Real-time console logging
- Client IP and destination logging
- Basic error handling
- No HTTPS decryption or SSL interception

## Architecture

```text
Browser / Client
       |
       v
Java Proxy Server
     Port 8888
       |
       +------ HTTP ------> Web Server
       |
       +----- HTTPS ------> Web Server
              CONNECT
              Tunnel
## Technologies ##
-Java
-Socket / ServerSocket
-Multithreading
-HTTP
-HTTPS
-Java I/O
-Running the Project

## Compile:

javac -d out src/Main.java src/server/*.java src/proxy/*.java src/logger/*.java

## Run:

java -cp out Main

The proxy listens on:

127.0.0.1:8888
Testing HTTP
curl -x http://127.0.0.1:8888 http://example.com/
Testing HTTPS
curl -x http://127.0.0.1:8888 https://example.com/
Browser Testing

Configure the browser/system proxy:

Host: 127.0.0.1
Port: 8888

The proxy will log HTTP requests and HTTPS CONNECT tunnels in the console.

HTTPS Handling

The proxy does not decrypt HTTPS traffic.

For HTTPS connections it handles the CONNECT request and creates a
TCP tunnel between the client and destination server.

Project Purpose

This project was created to understand:

-TCP socket communication
-Client-server architecture
-Java multithreading
-HTTP request forwarding
-HTTPS tunneling
-Java I/O streams
-Future Improvements
-Improved logging
-Configuration file
-Better connection management
-Proxy statistics
-Additional HTTP methods

---
