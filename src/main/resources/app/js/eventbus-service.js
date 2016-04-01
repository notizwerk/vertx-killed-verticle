angular.module('eventbus.service',[])
.service('eventBusService', function ($q,$rootScope,$location) {
  console.log("eventBusService created");
  this.eb = undefined;
  var self = this;

  this.started = function() {
    return (typeof this.eb !== "undefined" && this.eb.state == EventBus.OPEN );
  }
  
  this.start = function() {
    var q = $q.defer();
    var url = $location.protocol()+"://"+$location.host()+":"+$location.port()+"/eventbus";
    //var url = "http://localhost:8081/eventbus"; 
    console.log("eventbus @ "+url)
    this.eb = new EventBus(url);
    this.eb.defaultHeaders = {};
    this.eb.onopen = function() {
      q.resolve(self.eb);
      $rootScope.$apply(function() {                    
        $rootScope.$broadcast("eventbusStarted");
      });
    };
      
    this.eb.onclose = function () {
      // try reconnect
      console.log("eventbus closed");
      $rootScope.$apply(function() { 
        $rootScope.$broadcast("eventbusClosed");
      });
    };

    this.eb.onerror = function (error) {
      // try reconnect
      q.reject(error);
        console.log("eventbus error:", error);
      };
     return q.promise;
  }

  this.action = function(address, message, action, replyHandler) {
    this.send(address,message, {action : action},replyHandler);
  }

  this.send = function(address, message, headers, replyHandler) {
    if ( this.eb.state == EventBus.CLOSED || this.eb.state == EventBus.CLOSING ) {
      this.start().then(
        function(eventbus) {
          self.eb.send(address, message, headers, replyHandler);
        },
        function(error) {
          replyHandler(error);
        }
      );
    } else {
      this.eb.send(address, message, headers, replyHandler);
    }
  }
        
  this.restart = function() {
    console.log("restart eventbus");
    if (typeof self.eb !== "undefined") {
      self.eb.close();
    }
    self.start();
  }
  return this;
});    