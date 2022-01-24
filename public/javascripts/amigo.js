'use strict';

function makeRoleVariablesVisibilityToggleable() {
  $('input.show-role-variables').change(function() {
    var $this = $(this);
    var roleName = $this.data('role');
    var roleVariablesInput = $('input#role-'+roleName+'-variables');
    var checked = $this.prop('checked');
    if (!!checked)
      roleVariablesInput.removeClass('hidden');
    else
      roleVariablesInput.addClass('hidden');
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

$(function(){
  enablePostLinks();
  makeRoleVariablesVisibilityToggleable();
  initStickyComponents();
});
