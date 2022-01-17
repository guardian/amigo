'use strict';

var markdownConverter = new showdown.Converter();

function updateActiveRole() {
  var activeRole = window.location.hash.substring(1);
  $('.list-group-item.role-id').each(function(){
    var $this = $(this);
    var roleId = $this.data('role-id');
    if (roleId === activeRole) {
      $this.addClass('active');
      $('#explanation').addClass('hidden');
      $('#detail-'+roleId).removeClass('hidden');
    } else {
      $this.removeClass('active');
      $('#detail-'+roleId).addClass('hidden');
    }
  });
}

function updateActiveRoleOnHashChange() {
  $(window).bind('hashchange', updateActiveRole);
}

function initTabs() {
  $('.tab-label').find('a').click(function (e) {
    e.preventDefault();
    $(this).tab('show');
  });
}

function renderMarkdown() {
  $('.markdown').each(function() {
    this.innerHTML = markdownConverter.makeHtml($(this).find('pre').text());
  });
}

$(function(){
  updateActiveRole();
  updateActiveRoleOnHashChange();
  initTabs();
  renderMarkdown();
});
