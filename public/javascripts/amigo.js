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

$(function(){
  enablePostLinks();
  makeTableRowsClickable();
  makeRoleVariablesVisibilityToggleable();
});
