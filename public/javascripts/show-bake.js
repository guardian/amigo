'use strict';

function initPackerOutputComponent() {
  var div = $('#packer-output');
  var $window = $(window);

  function resizeDiv() {
    var newHeight = $window.height() - div.offset().top - 20;
    div.css('max-height', newHeight + 'px');
  }

  $window.resize(resizeDiv);
  resizeDiv();
}

function scrollToBottom() {
  var tailCheckbox = $("#tail-log");
  if (tailCheckbox.is(':checked')) {
    var div = $('#packer-output');
    var height = div.get(0).scrollHeight;
    div.animate({
      scrollTop: height
    }, 500);
  }
}

function initShowBakePage(eventSourceUrl, initialHighestLogNumber) {
  var highestLogNumber = initialHighestLogNumber;
  var packerOutputDiv = document.getElementById("packer-output");
  var amiIdDiv = document.getElementById("ami-id");
  var statusDiv = document.getElementById("status");

  var feed = new EventSource(eventSourceUrl);
  var handler = function(event){
    var msg = JSON.parse(event.data);
    switch (msg.eventType) {
      case "log":
        if (msg.log.number > highestLogNumber) {
          var html = "<div class=\"bake-log\">[" + msg.timestamp + "] " + msg.log.messageHtml + "</div>";
          packerOutputDiv.innerHTML += html;
          highestLogNumber = msg.log.number;
          scrollToBottom();
        }
        break;
      case "ami-created":
        amiIdDiv.innerText = msg.amiId;
        break;
      case "packer-process-exited":
        var status = msg.exitCode === 0 ? "Complete" : "Failed";
        statusDiv.innerText = status;
        break;
      default:
        console.log("Unsupported message received", msg);
    }
  };
  feed.addEventListener('message', handler, false);
}

$(function() {
  initPackerOutputComponent();
  scrollToBottom();
});
