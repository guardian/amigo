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
  scrollToBottom();
}

function scrollToBottom() {
  var div = $('#packer-output');
  var height = div.get(0).scrollHeight;
  div.animate({
    scrollTop: height,
    queue: false
  }, 500);
}

$(function() {
  initPackerOutputComponent();
  scrollToBottom();
});
