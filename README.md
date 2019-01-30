# liveview-clj

Server-side HTML rendering with reactive updates. Massively inspired by [Elixir's LiveView](https://dockyard.com/blog/2018/12/12/phoenix-liveview-interactive-real-time-apps-no-need-to-write-javascript) 

* WARNING: work in progress

## Description

liveview-clj renders HTML page, inject tiny runtime into it, correlate WebSocket connection with the rendered page, rerender page for each state's update and push updated HTML to client.

You can think about liveview-clj as stateful web handlers or Rich Web Application running on server side.

## Usage

See [examples](/examples/example/increment.clj)

## Start examples

`clj -A:examples` and open in browser http://localhost:8000/increment or http://localhost:8000/todo
