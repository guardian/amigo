'use strict';

function makeTableRowsClickable() {
  $('.table tr[data-href]').each(function(){
    $(this).css('cursor','pointer').hover(
      function(){
        $(this).addClass('active');
      },
      function(){
        $(this).removeClass('active');
      }).click( function(){
        document.location = $(this).attr('data-href');
      }
    );
  });
}

function makeRoleVariablesVisibilityToggleable() {
  $('input.show-role-variables').change(function() {
    var $this = $(this);
    var roleName = $this.data('role');
    var roleVariablesInput = $('input#role-'+roleName+'-variables');
    var checked = $this.prop('checked');
    if (!!checked)
      roleVariablesInput.css('visibility', 'visible');
    else
      roleVariablesInput.css('visibility', 'hidden');
  });
}

function enablePostLinks() {
  $('a.post').click(function() {
    var href = $(this).data('href');
    $('<form action="'+href+'" method="post"></form>').appendTo('body').submit();
  });
}

function relocateStickyComponents() {
  var windowTop = $(window).scrollTop();
  $('.sticky').each(function () {
    var $this = $(this);
    var $anchor = $this.prev(); // the component that you want to make sticky must be preceded by an empty div
    var componentTop = $anchor.offset().top;
    if (windowTop > componentTop - 60) {
      $this.addClass('stick');
      $anchor.height($this.outerHeight());
    } else {
      $this.removeClass('stick');
      $anchor.height(0);
    }
  });
}

function initStickyComponents() {
  $(window).scroll(relocateStickyComponents);
  relocateStickyComponents();
}

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



$(function(){
  enablePostLinks();
  makeTableRowsClickable();
  makeRoleVariablesVisibilityToggleable();
  initStickyComponents();
  initPackerOutputComponent();
});
