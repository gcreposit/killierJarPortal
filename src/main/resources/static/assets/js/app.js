$(function() {
	"use strict";

	// new PerfectScrollbar(".app-container"),
	
	// new PerfectScrollbar(".header-message-list"),
	// new PerfectScrollbar(".header-notifications-list"),
	
    $(".mobile-search-icon").on("click", function() {
			
		$(".search-bar").addClass("full-search-bar")
		 
	}), 
	
	  $(".search-close").on("click", function() {
	 	$(".search-bar").removeClass("full-search-bar")
      }), 
	
	
	$(".mobile-toggle-menu").on("click", function() {
		$(".wrapper").addClass("toggled")
	}),

	$(".toggle-icon").click(function() {
		$(".wrapper").hasClass("toggled") ? ($(".wrapper").removeClass("toggled"), $(".sidebar-wrapper").unbind("hover")) : ($(".wrapper").addClass("toggled"), $(".sidebar-wrapper").hover(function() {
			$(".wrapper").addClass("sidebar-hovered")
		}, function() {
			$(".wrapper").removeClass("sidebar-hovered")
		}))
	}),

		$(document).ready(function() {
		$(window).on("scroll", function() {
			$(this).scrollTop() > 300 ? $(".back-to-top").fadeIn() : $(".back-to-top").fadeOut()
		}), $(".back-to-top").on("click", function() {
			return $("html, body").animate({
				scrollTop: 0
			}, 600), !1
		})
	}),


	$(document).ready(function () {

		// Check for existing theme class on body and trigger corresponding function
		function checkAndTriggerTheme() {
			for (let i = 1; i <= 19; i++) {
				var customClass = 'bg-theme' + i;
				var themeFunction = 'theme' + i;

				if ($('body').hasClass(customClass)) {
					theme19();
					break;
				}
			}
		}

// Initial theme check and trigger
		checkAndTriggerTheme();



		$(window).on("scroll", function () {
				if ($(this).scrollTop() > 60) {
					$('.topbar').addClass('bg-info');
				} else {
					$('.topbar').removeClass('bg-info');
				}
			});
			$('.back-to-top').on("click", function () {
				$("html, body").animate({
					scrollTop: 0
				}, 600);
				return false;
			});
		});


	$(function() {
		for (var e = window.location, o = $(".metismenu li a").filter(function() {
				return this.href == e
			}).addClass("").parent().addClass("mm-active"); o.is("li");) o = o.parent("").addClass("mm-show").parent("").addClass("mm-active")
	}), $(function() {
		$("#menu").metisMenu()
	}), $(".chat-toggle-btn").on("click", function() {
		$(".chat-wrapper").toggleClass("chat-toggled")
	}), $(".chat-toggle-btn-mobile").on("click", function() {
		$(".chat-wrapper").removeClass("chat-toggled")
	}), $(".email-toggle-btn").on("click", function() {
		$(".email-wrapper").toggleClass("email-toggled")
	}), $(".email-toggle-btn-mobile").on("click", function() {
		$(".email-wrapper").removeClass("email-toggled")
	}), $(".compose-mail-btn").on("click", function() {
		$(".compose-mail-popup").show()
	}), $(".compose-mail-close").on("click", function() {
		$(".compose-mail-popup").hide()
	}),
	
	
	$(".switcher-btn").on("click", function() {
		$(".switcher-wrapper").toggleClass("switcher-toggled")
	}), $(".close-switcher").on("click", function() {
		$(".switcher-wrapper").removeClass("switcher-toggled")
	}),


	$('#theme1').click(theme1);
    $('#theme2').click(theme2);
    $('#theme3').click(theme3);
    $('#theme4').click(theme4);
    $('#theme5').click(theme5);
    $('#theme6').click(theme6);
    $('#theme7').click(theme7);
    $('#theme8').click(theme8);
    $('#theme9').click(theme9);
    $('#theme10').click(theme10);
    $('#theme11').click(theme11);
    $('#theme12').click(theme12);
    $('#theme13').click(theme13);
    $('#theme14').click(theme14);
    $('#theme15').click(theme15);
	$('#theme16').click(theme16);
	$('#theme17').click(theme17);
	$('#theme18').click(theme18);
	$('#theme19').click(theme19);

    function theme1() {
      $('body').attr('class', 'bg-theme bg-theme1');
    }

    function theme2() {
      $('body').attr('class', 'bg-theme bg-theme2');
    }

    function theme3() {
      $('body').attr('class', 'bg-theme bg-theme3');
    }

    function theme4() {
      $('body').attr('class', 'bg-theme bg-theme4');
    }
	
	function theme5() {
      $('body').attr('class', 'bg-theme bg-theme5');
    }
	
	function theme6() {
      $('body').attr('class', 'bg-theme bg-theme6');
    }

	function theme16() {
		$('body').attr('class', 'bg-theme bg-theme16');
		changeClassesToLightBg();
	}

	function theme17() {
		$('body').attr('class', 'bg-theme bg-theme17');
		changeClassesToLightBg();
	}

	function theme18() {
		$('body').attr('class', 'bg-theme bg-theme18');
		changeClassesToLightBg();
	}

	function theme19() {
		$('body').attr('class', 'bg-theme bg-theme19');
		changeClassesToLightBg();
	}

    function theme7() {
      $('body').attr('class', 'bg-theme bg-theme7');
    }

    function theme8() {
      $('body').attr('class', 'bg-theme bg-theme8');
    }

    function theme9() {
      $('body').attr('class', 'bg-theme bg-theme9');
    }

    function theme10() {
      $('body').attr('class', 'bg-theme bg-theme10');
    }

    function theme11() {
      $('body').attr('class', 'bg-theme bg-theme11');
    }

    function theme12() {
      $('body').attr('class', 'bg-theme bg-theme12');
    }

	function theme13() {
		$('body').attr('class', 'bg-theme bg-theme13');
	  }
	  
	  function theme14() {
		$('body').attr('class', 'bg-theme bg-theme14');
	  }
	  
	  function theme15() {
		$('body').attr('class', 'bg-theme bg-theme15');
	  }

	function updateClass(oldClass, newClass) {
		$('.' + oldClass).each(function() {
			$(this).addClass(newClass);
		});
	}

	function updateClasses(oldClass, newClasses) {
		$('.' + oldClass).each(function() {
			newClasses.forEach(function(newClass) {
				$(this).addClass(newClass);
			}, this);
		});
	}

	function replaceClass(oldClass, newClass){
		$('.' + oldClass).each(function() {
			$(this).removeClass(oldClass);
			$(this).addClass(newClass);
		});
	}

	function changeClassesToLightBg(){
		updateClass('sidebar-wrapper', 'sidebar-wrapper-custom');
		updateClass('sidebar-header', 'sidebar-header_custom');
		updateClass('topbar', 'topbar_custom');
		updateClass('breadcrumb_rg', 'breadcrumb_custom');
		updateClass('switcher-btn', 'switcher-btn_custom');
		updateClass('card', 'card_custom');
		updateClasses('page-breadcrumb', ['font-weight-bold','text-dark','mx-3']);
		updateClass('has-arrow', 'has-arrow_custom rounded-3');
		replaceClass('text-white', 'text-dark');
		replaceClass('text-light', 'text-dark');
		replaceClass('logo-text', 'logo-text_custom');
		updateClass('parent-icon', 'parent-icon_custom')
		updateClass('menu-title', 'menu-title_custom')
	}



});