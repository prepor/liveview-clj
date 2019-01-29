var LiveView = function(endpoint) {
  var socket = new WebSocket(endpoint);
  socket.onmessage = function(e) {
    var data = JSON.parse(e.data);
    if (data.type == "rerender"){
      morphdom(document.documentElement, data.value);
    }
  };
  return {
    socket: socket,
    sendEvent: function(type, event) {
      console.log(event);
      if (this.socket.readyState != 1) {
        console.log("Can't send an event, socket is not connected")
      } else {
        this.socket.send(JSON.stringify({type: "event", event: type, payload: event}));
      }
    }
  };
};
