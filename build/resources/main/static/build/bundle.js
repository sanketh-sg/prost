/*
 * ATTENTION: The "eval" devtool has been used (maybe by default in mode: "development").
 * This devtool is neither made for production nor for readable output files.
 * It uses "eval()" calls to create a separate source file in the browser devtools.
 * If you are trying to read the output file, select a different devtool (https://webpack.js.org/configuration/devtool/)
 * or disable the default devtool with "devtool: false".
 * If you are looking for production-ready output files, see mode: "production" (https://webpack.js.org/configuration/mode/).
 */
var prostLib;
/******/ (() => { // webpackBootstrap
/******/ 	"use strict";
/******/ 	var __webpack_modules__ = ({

/***/ "./frontend/form.js":
/*!**************************!*\
  !*** ./frontend/form.js ***!
  \**************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   \"pagination\": () => (/* binding */ pagination)\n/* harmony export */ });\nconst pagination = {\r\n    changePage: elem => {\r\n        if (elem.classList.contains('disabled')) {\r\n            return;\r\n        }\r\n\r\n        let pageInput = document.querySelector(\r\n            '#pagination-form input[name=\"page\"]'\r\n        );\r\n\r\n        if (\r\n            document.querySelector('#pagination-form input[name=\"page\"]') ==\r\n            null\r\n        ) {\r\n            document\r\n                .querySelector('#pagination-form')\r\n                .insertAdjacentHTML(\r\n                    'afterbegin',\r\n                    `<input type=\"hidden\" name=\"page\" value=\"${0}\">`\r\n                );\r\n\r\n            pageInput = document.querySelector(\r\n                '#pagination-form input[name=\"page\"]'\r\n            );\r\n        }\r\n\r\n        const currentPage = parseInt(pageInput.value);\r\n        const maxPage = parseInt(\r\n            document\r\n                .querySelector('#pagination-form')\r\n                .getAttribute('data-pagination-max-page')\r\n        );\r\n\r\n        switch (elem.getAttribute('data-pagination')) {\r\n            case '0':\r\n                pageInput.remove();\r\n                break;\r\n            case '+1':\r\n                if (currentPage + 1 > maxPage) {\r\n                    pageInput.value = maxPage;\r\n                } else {\r\n                    pageInput.value = currentPage + 1;\r\n                }\r\n                break;\r\n            case '-1':\r\n                if (currentPage - 1 <= 0) {\r\n                    pageInput.value = 0;\r\n                } else {\r\n                    pageInput.value = currentPage - 1;\r\n                }\r\n                break;\r\n            case 'max':\r\n                pageInput.value = maxPage;\r\n                break;\r\n        }\r\n        document.querySelector('#pagination-form').submit();\r\n    },\r\n};\r\n\n\n//# sourceURL=webpack://uni_bamberg_proj/./frontend/form.js?");

/***/ }),

/***/ "./frontend/index.js":
/*!***************************!*\
  !*** ./frontend/index.js ***!
  \***************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   \"pagination\": () => (/* reexport safe */ _form__WEBPACK_IMPORTED_MODULE_7__.pagination),\n/* harmony export */   \"transitions\": () => (/* reexport safe */ _transitions__WEBPACK_IMPORTED_MODULE_6__.transitions)\n/* harmony export */ });\n/* harmony import */ var _index_css__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./index.css */ \"./frontend/index.css\");\n/* harmony import */ var _layout_css__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./layout.css */ \"./frontend/layout.css\");\n/* harmony import */ var _elements_css__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ./elements.css */ \"./frontend/elements.css\");\n/* harmony import */ var _nv_css__WEBPACK_IMPORTED_MODULE_3__ = __webpack_require__(/*! ./nv.css */ \"./frontend/nv.css\");\n/* harmony import */ var _search_section_css__WEBPACK_IMPORTED_MODULE_4__ = __webpack_require__(/*! ./search_section.css */ \"./frontend/search_section.css\");\n/* harmony import */ var _cardContent_css__WEBPACK_IMPORTED_MODULE_5__ = __webpack_require__(/*! ./cardContent.css */ \"./frontend/cardContent.css\");\n/* harmony import */ var _transitions__WEBPACK_IMPORTED_MODULE_6__ = __webpack_require__(/*! ./transitions */ \"./frontend/transitions.js\");\n/* harmony import */ var _form__WEBPACK_IMPORTED_MODULE_7__ = __webpack_require__(/*! ./form */ \"./frontend/form.js\");\n/* harmony import */ var _orders_css__WEBPACK_IMPORTED_MODULE_8__ = __webpack_require__(/*! ./orders.css */ \"./frontend/orders.css\");\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\n\n//# sourceURL=webpack://uni_bamberg_proj/./frontend/index.js?");

/***/ }),

/***/ "./frontend/transitions.js":
/*!*********************************!*\
  !*** ./frontend/transitions.js ***!
  \*********************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   \"transitions\": () => (/* binding */ transitions)\n/* harmony export */ });\nconst transitions = {\r\n    slideOffScreen: selector => {\r\n        const elem = document.querySelector(selector);\r\n        elem.animate([{ transform: `translateX(${screen.width}px)` }], {\r\n            duration: 500,\r\n            iterations: 1,\r\n        });\r\n        setTimeout(() => (elem.style.display = 'none'), 500);\r\n    },\r\n};\n\n//# sourceURL=webpack://uni_bamberg_proj/./frontend/transitions.js?");

/***/ }),

/***/ "./frontend/cardContent.css":
/*!**********************************!*\
  !*** ./frontend/cardContent.css ***!
  \**********************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n\n//# sourceURL=webpack://uni_bamberg_proj/./frontend/cardContent.css?");

/***/ }),

/***/ "./frontend/elements.css":
/*!*******************************!*\
  !*** ./frontend/elements.css ***!
  \*******************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n\n//# sourceURL=webpack://uni_bamberg_proj/./frontend/elements.css?");

/***/ }),

/***/ "./frontend/index.css":
/*!****************************!*\
  !*** ./frontend/index.css ***!
  \****************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n\n//# sourceURL=webpack://uni_bamberg_proj/./frontend/index.css?");

/***/ }),

/***/ "./frontend/layout.css":
/*!*****************************!*\
  !*** ./frontend/layout.css ***!
  \*****************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n\n//# sourceURL=webpack://uni_bamberg_proj/./frontend/layout.css?");

/***/ }),

/***/ "./frontend/nv.css":
/*!*************************!*\
  !*** ./frontend/nv.css ***!
  \*************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n\n//# sourceURL=webpack://uni_bamberg_proj/./frontend/nv.css?");

/***/ }),

/***/ "./frontend/orders.css":
/*!*****************************!*\
  !*** ./frontend/orders.css ***!
  \*****************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n\n//# sourceURL=webpack://uni_bamberg_proj/./frontend/orders.css?");

/***/ }),

/***/ "./frontend/search_section.css":
/*!*************************************!*\
  !*** ./frontend/search_section.css ***!
  \*************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n\n//# sourceURL=webpack://uni_bamberg_proj/./frontend/search_section.css?");

/***/ })

/******/ 	});
/************************************************************************/
/******/ 	// The module cache
/******/ 	var __webpack_module_cache__ = {};
/******/ 	
/******/ 	// The require function
/******/ 	function __webpack_require__(moduleId) {
/******/ 		// Check if module is in cache
/******/ 		var cachedModule = __webpack_module_cache__[moduleId];
/******/ 		if (cachedModule !== undefined) {
/******/ 			return cachedModule.exports;
/******/ 		}
/******/ 		// Create a new module (and put it into the cache)
/******/ 		var module = __webpack_module_cache__[moduleId] = {
/******/ 			// no module.id needed
/******/ 			// no module.loaded needed
/******/ 			exports: {}
/******/ 		};
/******/ 	
/******/ 		// Execute the module function
/******/ 		__webpack_modules__[moduleId](module, module.exports, __webpack_require__);
/******/ 	
/******/ 		// Return the exports of the module
/******/ 		return module.exports;
/******/ 	}
/******/ 	
/************************************************************************/
/******/ 	/* webpack/runtime/define property getters */
/******/ 	(() => {
/******/ 		// define getter functions for harmony exports
/******/ 		__webpack_require__.d = (exports, definition) => {
/******/ 			for(var key in definition) {
/******/ 				if(__webpack_require__.o(definition, key) && !__webpack_require__.o(exports, key)) {
/******/ 					Object.defineProperty(exports, key, { enumerable: true, get: definition[key] });
/******/ 				}
/******/ 			}
/******/ 		};
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/hasOwnProperty shorthand */
/******/ 	(() => {
/******/ 		__webpack_require__.o = (obj, prop) => (Object.prototype.hasOwnProperty.call(obj, prop))
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/make namespace object */
/******/ 	(() => {
/******/ 		// define __esModule on exports
/******/ 		__webpack_require__.r = (exports) => {
/******/ 			if(typeof Symbol !== 'undefined' && Symbol.toStringTag) {
/******/ 				Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
/******/ 			}
/******/ 			Object.defineProperty(exports, '__esModule', { value: true });
/******/ 		};
/******/ 	})();
/******/ 	
/************************************************************************/
/******/ 	
/******/ 	// startup
/******/ 	// Load entry module and return exports
/******/ 	// This entry module can't be inlined because the eval devtool is used.
/******/ 	var __webpack_exports__ = __webpack_require__("./frontend/index.js");
/******/ 	prostLib = __webpack_exports__;
/******/ 	
/******/ })()
;