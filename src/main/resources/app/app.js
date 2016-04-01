'use strict';

angular.module('myApp', [ "eventbus.service" ])
.controller('ConsumerProducerCtrl', ["$scope","eventBusService",function($scope,eventBusService) {
  $scope.consumer = [];
  $scope.producer = {};
  
  
  $scope.sendAction = function(action) {
      eventBusService.action("producer.control",{},action);
      console.log("send "+action);
  }

  $scope.sendThrottle = function(throttle) {
      eventBusService.action("producer.control",{throttle:parseInt(throttle)},"throttle");
      console.log("send throttle"+throttle);
  }
  
  $scope.$on("eventbusStarted",function(event){
    eventBusService.eb.registerHandler("consumer.stats", function(err,msg) {
        $scope.$apply(function() {
            var json = msg.body;
            var consumerFound = false;
            var i;
            for (i=0; i < $scope.consumer.length ; i++ ) {
                var c = $scope.consumer[i];
                if ( c.id === json.id) {
                   consumerFound = true;
                   Object.assign($scope.consumer[i],json);
                }
           }
           if (consumerFound === false ) {
              $scope.consumer.push(json);
           }
        });
    });
    eventBusService.eb.registerHandler("producer.stats", function(err,msg) {
        $scope.$apply(function() {
            var json = msg.body;
            Object.assign($scope.producer,json);
        });
    });
    
    
  });
  
}])
.run(["eventBusService",function(eventBusService) {
  eventBusService.start();  
}]);