var LiveView = function(endpoint) {
  var running = true;
  var obj = {
    socket: null,
    sendEvent: function(type, event) {
      console.log(event);
      if (this.socket.readyState != 1) {
        console.log("Can't send an event, socket is not connected")
      } else {
        this.socket.send(JSON.stringify({type: "event", event: type, payload: event}));
      }
    },
    connect: function() {
      obj.socket = new WebSocket(endpoint);
      obj.socket.onmessage = function(e) {
        var data = JSON.parse(e.data);
        if (data.type == "rerender"){
          morphdom(document.documentElement, data.value);
        }
      };
      obj.socket.onclose = function(e) {
        if (running) {
          setTimeout(function() { obj.connect() }, 1000)
        }
      };
    },
    stop: function() {
      running = false;
      obj.socket.close()
    }
  };
  obj.connect();
  return obj;
};
