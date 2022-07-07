/*!
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @license Apache-2.0
 */

(function(f){if(typeof exports==="object"&&typeof module!=="undefined"){module.exports=f()}else if(typeof define==="function"&&define.amd){define([],f)}else{var g;if(typeof window!=="undefined"){g=window}else if(typeof global!=="undefined"){g=global}else if(typeof self!=="undefined"){g=self}else{g=this}g.unomiTracker = f()}})(function(){var define,module,exports;return (function(){function r(e,n,t){function o(i,f){if(!n[i]){if(!e[i]){var c="function"==typeof require&&require;if(!f&&c)return c(i,!0);if(u)return u(i,!0);var a=new Error("Cannot find module '"+i+"'");throw a.code="MODULE_NOT_FOUND",a}var p=n[i]={exports:{}};e[i][0].call(p.exports,function(r){var n=e[i][1][r];return o(n||r)},p,p.exports,r,e,n,t)}return n[i].exports}for(var u="function"==typeof require&&require,i=0;i<t.length;i++)o(t[i]);return o}return r})()({1:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var arity = require('@ndhoule/arity');

var objToString = Object.prototype.toString;

/**
 * Determine if a value is a function.
 *
 * @param {*} val
 * @return {boolean}
 */
// TODO: Move to lib
var isFunction = function(val) {
  return typeof val === 'function';
};

/**
 * Determine if a value is a number.
 *
 * @param {*} val
 * @return {boolean}
 */
// TODO: Move to lib
var isNumber = function(val) {
  var type = typeof val;
  return type === 'number' || (type === 'object' && objToString.call(val) === '[object Number]');
};

/**
 * Wrap a function `fn` in a function that will invoke `fn` when invoked `n` or
 * more times.
 *
 * @name after
 * @api public
 * @category Function
 * @param {Number} n The number of
 * @param {Function} fn The function to wrap.
 * @return {Function} A function that will call `fn` after `n` or more
 * invocations.
 * @example
 */
var after = function after(n, fn) {
  if (!isNumber(n)) {
    throw new TypeError('Expected a number but received ' + typeof n);
  }

  if (!isFunction(fn)) {
    throw new TypeError('Expected a function but received ' + typeof fn);
  }

  var callCount = 0;

  return arity(fn.length, function() {
    callCount += 1;

    if (callCount < n) {
      return;
    }

    return fn.apply(this, arguments);
  });
};

/*
 * Exports.
 */

module.exports = after;

},{"@ndhoule/arity":2}],2:[function(require,module,exports){
'use strict';

var objToString = Object.prototype.toString;

/**
 * Determine if a value is a function.
 *
 * @param {*} val
 * @return {boolean}
 */
// TODO: Move to lib
var isFunction = function(val) {
  return typeof val === 'function';
};

/**
 * Determine if a value is a number.
 *
 * @param {*} val
 * @return {boolean}
 */
// TODO: Move to lib
var isNumber = function(val) {
  var type = typeof val;
  return type === 'number' || (type === 'object' && objToString.call(val) === '[object Number]');
};

 /**
  * Creates an array of generic, numbered argument names.
  *
  * @name createParams
  * @api private
  * @param {number} n
  * @return {Array}
  * @example
  * argNames(2);
  * //=> ['arg1', 'arg2']
  */
var createParams = function createParams(n) {
  var args = [];

  for (var i = 1; i <= n; i += 1) {
    args.push('arg' + i);
  }

  return args;
};

 /**
  * Dynamically construct a wrapper function of `n` arity that.
  *
  * If at all possible, prefer a function from the arity wrapper cache above to
  * avoid allocating a new function at runtime.
  *
  * @name createArityWrapper
  * @api private
  * @param {number} n
  * @return {Function(Function)}
  */
var createArityWrapper = function createArityWrapper(n) {
  var paramNames = createParams(n).join(', ');
  var wrapperBody = ''.concat(
    '  return function(', paramNames, ') {\n',
    '    return func.apply(this, arguments);\n',
    '  };'
  );

  /* eslint-disable no-new-func */
  return new Function('func', wrapperBody);
  /* eslint-enable no-new-func */
};

// Cache common arity wrappers to avoid constructing them at runtime
var arityWrapperCache = [
  /* eslint-disable no-unused-vars */
  function(fn) {
    return function() {
      return fn.apply(this, arguments);
    };
  },

  function(fn) {
    return function(arg1) {
      return fn.apply(this, arguments);
    };
  },

  function(fn) {
    return function(arg1, arg2) {
      return fn.apply(this, arguments);
    };
  },

  function(fn) {
    return function(arg1, arg2, arg3) {
      return fn.apply(this, arguments);
    };
  },

  function(fn) {
    return function(arg1, arg2, arg3, arg4) {
      return fn.apply(this, arguments);
    };
  },

  function(fn) {
    return function(arg1, arg2, arg3, arg4, arg5) {
      return fn.apply(this, arguments);
    };
  }
  /* eslint-enable no-unused-vars */
];

/**
 * Takes a function and an [arity](https://en.wikipedia.org/wiki/Arity) `n`, and returns a new
 * function that expects `n` arguments.
 *
 * @name arity
 * @api public
 * @category Function
 * @see {@link curry}
 * @param {Number} n The desired arity of the returned function.
 * @param {Function} fn The function to wrap.
 * @return {Function} A function of n arity, wrapping `fn`.
 * @example
 * var add = function(a, b) {
 *   return a + b;
 * };
 *
 * // Check the number of arguments this function expects by accessing `.length`:
 * add.length;
 * //=> 2
 *
 * var unaryAdd = arity(1, add);
 * unaryAdd.length;
 * //=> 1
 */
var arity = function arity(n, func) {
  if (!isFunction(func)) {
    throw new TypeError('Expected a function but got ' + typeof func);
  }

  n = Math.max(isNumber(n) ? n : 0, 0);

  if (!arityWrapperCache[n]) {
    arityWrapperCache[n] = createArityWrapper(n);
  }

  return arityWrapperCache[n](func);
};

/*
 * Exports.
 */

module.exports = arity;

},{}],3:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var type = require('component-type');

/**
 * Deeply clone an object.
 *
 * @param {*} obj Any object.
 */

var clone = function clone(obj) {
  var t = type(obj);

  if (t === 'object') {
    var copy = {};
    for (var key in obj) {
      if (obj.hasOwnProperty(key)) {
        copy[key] = clone(obj[key]);
      }
    }
    return copy;
  }

  if (t === 'array') {
    var copy = new Array(obj.length);
    for (var i = 0, l = obj.length; i < l; i++) {
      copy[i] = clone(obj[i]);
    }
    return copy;
  }

  if (t === 'regexp') {
    // from millermedeiros/amd-utils - MIT
    var flags = '';
    flags += obj.multiline ? 'm' : '';
    flags += obj.global ? 'g' : '';
    flags += obj.ignoreCase ? 'i' : '';
    return new RegExp(obj.source, flags);
  }

  if (t === 'date') {
    return new Date(obj.getTime());
  }

  // string, number, boolean, etc.
  return obj;
};

/*
 * Exports.
 */

module.exports = clone;

},{"component-type":57}],4:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var drop = require('@ndhoule/drop');
var rest = require('@ndhoule/rest');

var has = Object.prototype.hasOwnProperty;
var objToString = Object.prototype.toString;

/**
 * Returns `true` if a value is an object, otherwise `false`.
 *
 * @name isObject
 * @api private
 * @param {*} val The value to test.
 * @return {boolean}
 */
// TODO: Move to a library
var isObject = function isObject(value) {
  return Boolean(value) && typeof value === 'object';
};

/**
 * Returns `true` if a value is a plain object, otherwise `false`.
 *
 * @name isPlainObject
 * @api private
 * @param {*} val The value to test.
 * @return {boolean}
 */
// TODO: Move to a library
var isPlainObject = function isPlainObject(value) {
  return Boolean(value) && objToString.call(value) === '[object Object]';
};

/**
 * Assigns a key-value pair to a target object when the value assigned is owned,
 * and where target[key] is undefined.
 *
 * @name shallowCombiner
 * @api private
 * @param {Object} target
 * @param {Object} source
 * @param {*} value
 * @param {string} key
 */
var shallowCombiner = function shallowCombiner(target, source, value, key) {
  if (has.call(source, key) && target[key] === undefined) {
    target[key] = value;
  }
  return source;
};

/**
 * Assigns a key-value pair to a target object when the value assigned is owned,
 * and where target[key] is undefined; also merges objects recursively.
 *
 * @name deepCombiner
 * @api private
 * @param {Object} target
 * @param {Object} source
 * @param {*} value
 * @param {string} key
 * @return {Object}
 */
var deepCombiner = function(target, source, value, key) {
  if (has.call(source, key)) {
    if (isPlainObject(target[key]) && isPlainObject(value)) {
        target[key] = defaultsDeep(target[key], value);
    } else if (target[key] === undefined) {
        target[key] = value;
    }
  }

  return source;
};

/**
 * TODO: Document
 *
 * @name defaultsWith
 * @api private
 * @param {Function} combiner
 * @param {Object} target
 * @param {...Object} sources
 * @return {Object} Return the input `target`.
 */
var defaultsWith = function(combiner, target /*, ...sources */) {
  if (!isObject(target)) {
    return target;
  }

  combiner = combiner || shallowCombiner;
  var sources = drop(2, arguments);

  for (var i = 0; i < sources.length; i += 1) {
    for (var key in sources[i]) {
      combiner(target, sources[i], sources[i][key], key);
    }
  }

  return target;
};

/**
 * Copies owned, enumerable properties from a source object(s) to a target
 * object when the value of that property on the source object is `undefined`.
 * Recurses on objects.
 *
 * @name defaultsDeep
 * @api public
 * @param {Object} target
 * @param {...Object} sources
 * @return {Object} The input `target`.
 */
var defaultsDeep = function defaultsDeep(target /*, sources */) {
  // TODO: Replace with `partial` call?
  return defaultsWith.apply(null, [deepCombiner, target].concat(rest(arguments)));
};

/**
 * Copies owned, enumerable properties from a source object(s) to a target
 * object when the value of that property on the source object is `undefined`.
 *
 * @name defaults
 * @api public
 * @param {Object} target
 * @param {...Object} sources
 * @return {Object}
 * @example
 * var a = { a: 1 };
 * var b = { a: 2, b: 2 };
 *
 * defaults(a, b);
 * console.log(a); //=> { a: 1, b: 2 }
 */
var defaults = function(target /*, ...sources */) {
  // TODO: Replace with `partial` call?
  return defaultsWith.apply(null, [null, target].concat(rest(arguments)));
};

/*
 * Exports.
 */

module.exports = defaults;
module.exports.deep = defaultsDeep;

},{"@ndhoule/drop":5,"@ndhoule/rest":14}],5:[function(require,module,exports){
'use strict';

var max = Math.max;

/**
 * Produce a new array composed of all but the first `n` elements of an input `collection`.
 *
 * @name drop
 * @api public
 * @param {number} count The number of elements to drop.
 * @param {Array} collection The collection to iterate over.
 * @return {Array} A new array containing all but the first element from `collection`.
 * @example
 * drop(0, [1, 2, 3]); // => [1, 2, 3]
 * drop(1, [1, 2, 3]); // => [2, 3]
 * drop(2, [1, 2, 3]); // => [3]
 * drop(3, [1, 2, 3]); // => []
 * drop(4, [1, 2, 3]); // => []
 */
var drop = function drop(count, collection) {
  var length = collection ? collection.length : 0;

  if (!length) {
    return [];
  }

  // Preallocating an array *significantly* boosts performance when dealing with
  // `arguments` objects on v8. For a summary, see:
  // https://github.com/petkaantonov/bluebird/wiki/Optimization-killers#32-leaking-arguments
  var toDrop = max(Number(count) || 0, 0);
  var resultsLength = max(length - toDrop, 0);
  var results = new Array(resultsLength);

  for (var i = 0; i < resultsLength; i += 1) {
    results[i] = collection[i + toDrop];
  }

  return results;
};

/*
 * Exports.
 */

module.exports = drop;

},{}],6:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var keys = require('@ndhoule/keys');

var objToString = Object.prototype.toString;

/**
 * Tests if a value is a number.
 *
 * @name isNumber
 * @api private
 * @param {*} val The value to test.
 * @return {boolean} Returns `true` if `val` is a number, otherwise `false`.
 */
// TODO: Move to library
var isNumber = function isNumber(val) {
  var type = typeof val;
  return type === 'number' || (type === 'object' && objToString.call(val) === '[object Number]');
};

/**
 * Tests if a value is an array.
 *
 * @name isArray
 * @api private
 * @param {*} val The value to test.
 * @return {boolean} Returns `true` if the value is an array, otherwise `false`.
 */
// TODO: Move to library
var isArray = typeof Array.isArray === 'function' ? Array.isArray : function isArray(val) {
  return objToString.call(val) === '[object Array]';
};

/**
 * Tests if a value is array-like. Array-like means the value is not a function and has a numeric
 * `.length` property.
 *
 * @name isArrayLike
 * @api private
 * @param {*} val
 * @return {boolean}
 */
// TODO: Move to library
var isArrayLike = function isArrayLike(val) {
  return val != null && (isArray(val) || (val !== 'function' && isNumber(val.length)));
};

/**
 * Internal implementation of `each`. Works on arrays and array-like data structures.
 *
 * @name arrayEach
 * @api private
 * @param {Function(value, key, collection)} iterator The function to invoke per iteration.
 * @param {Array} array The array(-like) structure to iterate over.
 * @return {undefined}
 */
var arrayEach = function arrayEach(iterator, array) {
  for (var i = 0; i < array.length; i += 1) {
    // Break iteration early if `iterator` returns `false`
    if (iterator(array[i], i, array) === false) {
      break;
    }
  }
};

/**
 * Internal implementation of `each`. Works on objects.
 *
 * @name baseEach
 * @api private
 * @param {Function(value, key, collection)} iterator The function to invoke per iteration.
 * @param {Object} object The object to iterate over.
 * @return {undefined}
 */
var baseEach = function baseEach(iterator, object) {
  var ks = keys(object);

  for (var i = 0; i < ks.length; i += 1) {
    // Break iteration early if `iterator` returns `false`
    if (iterator(object[ks[i]], ks[i], object) === false) {
      break;
    }
  }
};

/**
 * Iterate over an input collection, invoking an `iterator` function for each element in the
 * collection and passing to it three arguments: `(value, index, collection)`. The `iterator`
 * function can end iteration early by returning `false`.
 *
 * @name each
 * @api public
 * @param {Function(value, key, collection)} iterator The function to invoke per iteration.
 * @param {Array|Object|string} collection The collection to iterate over.
 * @return {undefined} Because `each` is run only for side effects, always returns `undefined`.
 * @example
 * var log = console.log.bind(console);
 *
 * each(log, ['a', 'b', 'c']);
 * //-> 'a', 0, ['a', 'b', 'c']
 * //-> 'b', 1, ['a', 'b', 'c']
 * //-> 'c', 2, ['a', 'b', 'c']
 * //=> undefined
 *
 * each(log, 'tim');
 * //-> 't', 2, 'tim'
 * //-> 'i', 1, 'tim'
 * //-> 'm', 0, 'tim'
 * //=> undefined
 *
 * // Note: Iteration order not guaranteed across environments
 * each(log, { name: 'tim', occupation: 'enchanter' });
 * //-> 'tim', 'name', { name: 'tim', occupation: 'enchanter' }
 * //-> 'enchanter', 'occupation', { name: 'tim', occupation: 'enchanter' }
 * //=> undefined
 */
var each = function each(iterator, collection) {
  return (isArrayLike(collection) ? arrayEach : baseEach).call(this, iterator, collection);
};

/*
 * Exports.
 */

module.exports = each;

},{"@ndhoule/keys":11}],7:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var each = require('@ndhoule/each');

/**
 * Check if a predicate function returns `true` for all values in a `collection`.
 * Checks owned, enumerable values and exits early when `predicate` returns
 * `false`.
 *
 * @name every
 * @param {Function} predicate The function used to test values.
 * @param {Array|Object|string} collection The collection to search.
 * @return {boolean} True if all values passes the predicate test, otherwise false.
 * @example
 * var isEven = function(num) { return num % 2 === 0; };
 *
 * every(isEven, []); // => true
 * every(isEven, [1, 2]); // => false
 * every(isEven, [2, 4, 6]); // => true
 */
var every = function every(predicate, collection) {
  if (typeof predicate !== 'function') {
    throw new TypeError('`predicate` must be a function but was a ' + typeof predicate);
  }

  var result = true;

  each(function(val, key, collection) {
    result = !!predicate(val, key, collection);

    // Exit early
    if (!result) {
      return false;
    }
  }, collection);

  return result;
};

/*
 * Exports.
 */

module.exports = every;

},{"@ndhoule/each":6}],8:[function(require,module,exports){
'use strict';

var has = Object.prototype.hasOwnProperty;

/**
 * Copy the properties of one or more `objects` onto a destination object. Input objects are iterated over
 * in left-to-right order, so duplicate properties on later objects will overwrite those from
 * erevious ones. Only enumerable and own properties of the input objects are copied onto the
 * resulting object.
 *
 * @name extend
 * @api public
 * @category Object
 * @param {Object} dest The destination object.
 * @param {...Object} sources The source objects.
 * @return {Object} `dest`, extended with the properties of all `sources`.
 * @example
 * var a = { a: 'a' };
 * var b = { b: 'b' };
 * var c = { c: 'c' };
 *
 * extend(a, b, c);
 * //=> { a: 'a', b: 'b', c: 'c' };
 */
var extend = function extend(dest /*, sources */) {
  var sources = Array.prototype.slice.call(arguments, 1);

  for (var i = 0; i < sources.length; i += 1) {
    for (var key in sources[i]) {
      if (has.call(sources[i], key)) {
        dest[key] = sources[i][key];
      }
    }
  }

  return dest;
};

/*
 * Exports.
 */

module.exports = extend;

},{}],9:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var each = require('@ndhoule/each');

/**
 * Reduces all the values in a collection down into a single value. Does so by iterating through the
 * collection from left to right, repeatedly calling an `iterator` function and passing to it four
 * arguments: `(accumulator, value, index, collection)`.
 *
 * Returns the final return value of the `iterator` function.
 *
 * @name foldl
 * @api public
 * @param {Function} iterator The function to invoke per iteration.
 * @param {*} accumulator The initial accumulator value, passed to the first invocation of `iterator`.
 * @param {Array|Object} collection The collection to iterate over.
 * @return {*} The return value of the final call to `iterator`.
 * @example
 * foldl(function(total, n) {
 *   return total + n;
 * }, 0, [1, 2, 3]);
 * //=> 6
 *
 * var phonebook = { bob: '555-111-2345', tim: '655-222-6789', sheila: '655-333-1298' };
 *
 * foldl(function(results, phoneNumber) {
 *  if (phoneNumber[0] === '6') {
 *    return results.concat(phoneNumber);
 *  }
 *  return results;
 * }, [], phonebook);
 * // => ['655-222-6789', '655-333-1298']
 */
var foldl = function foldl(iterator, accumulator, collection) {
  if (typeof iterator !== 'function') {
    throw new TypeError('Expected a function but received a ' + typeof iterator);
  }

  each(function(val, i, collection) {
    accumulator = iterator(accumulator, val, i, collection);
  }, collection);

  return accumulator;
};

/*
 * Exports.
 */

module.exports = foldl;

},{"@ndhoule/each":6}],10:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var each = require('@ndhoule/each');

var strIndexOf = String.prototype.indexOf;

/**
 * Object.is/sameValueZero polyfill.
 *
 * @api private
 * @param {*} value1
 * @param {*} value2
 * @return {boolean}
 */
// TODO: Move to library
var sameValueZero = function sameValueZero(value1, value2) {
  // Normal values and check for 0 / -0
  if (value1 === value2) {
    return value1 !== 0 || 1 / value1 === 1 / value2;
  }
  // NaN
  return value1 !== value1 && value2 !== value2;
};

/**
 * Searches a given `collection` for a value, returning true if the collection
 * contains the value and false otherwise. Can search strings, arrays, and
 * objects.
 *
 * @name includes
 * @api public
 * @param {*} searchElement The element to search for.
 * @param {Object|Array|string} collection The collection to search.
 * @return {boolean}
 * @example
 * includes(2, [1, 2, 3]);
 * //=> true
 *
 * includes(4, [1, 2, 3]);
 * //=> false
 *
 * includes(2, { a: 1, b: 2, c: 3 });
 * //=> true
 *
 * includes('a', { a: 1, b: 2, c: 3 });
 * //=> false
 *
 * includes('abc', 'xyzabc opq');
 * //=> true
 *
 * includes('nope', 'xyzabc opq');
 * //=> false
 */
var includes = function includes(searchElement, collection) {
  var found = false;

  // Delegate to String.prototype.indexOf when `collection` is a string
  if (typeof collection === 'string') {
    return strIndexOf.call(collection, searchElement) !== -1;
  }

  // Iterate through enumerable/own array elements and object properties.
  each(function(value) {
    if (sameValueZero(value, searchElement)) {
      found = true;
      // Exit iteration early when found
      return false;
    }
  }, collection);

  return found;
};

/*
 * Exports.
 */

module.exports = includes;

},{"@ndhoule/each":6}],11:[function(require,module,exports){
'use strict';

var hop = Object.prototype.hasOwnProperty;
var strCharAt = String.prototype.charAt;
var toStr = Object.prototype.toString;

/**
 * Returns the character at a given index.
 *
 * @param {string} str
 * @param {number} index
 * @return {string|undefined}
 */
// TODO: Move to a library
var charAt = function(str, index) {
  return strCharAt.call(str, index);
};

/**
 * hasOwnProperty, wrapped as a function.
 *
 * @name has
 * @api private
 * @param {*} context
 * @param {string|number} prop
 * @return {boolean}
 */

// TODO: Move to a library
var has = function has(context, prop) {
  return hop.call(context, prop);
};

/**
 * Returns true if a value is a string, otherwise false.
 *
 * @name isString
 * @api private
 * @param {*} val
 * @return {boolean}
 */

// TODO: Move to a library
var isString = function isString(val) {
  return toStr.call(val) === '[object String]';
};

/**
 * Returns true if a value is array-like, otherwise false. Array-like means a
 * value is not null, undefined, or a function, and has a numeric `length`
 * property.
 *
 * @name isArrayLike
 * @api private
 * @param {*} val
 * @return {boolean}
 */
// TODO: Move to a library
var isArrayLike = function isArrayLike(val) {
  return val != null && (typeof val !== 'function' && typeof val.length === 'number');
};


/**
 * indexKeys
 *
 * @name indexKeys
 * @api private
 * @param {} target
 * @param {Function} pred
 * @return {Array}
 */
var indexKeys = function indexKeys(target, pred) {
  pred = pred || has;

  var results = [];

  for (var i = 0, len = target.length; i < len; i += 1) {
    if (pred(target, i)) {
      results.push(String(i));
    }
  }

  return results;
};

/**
 * Returns an array of an object's owned keys.
 *
 * @name objectKeys
 * @api private
 * @param {*} target
 * @param {Function} pred Predicate function used to include/exclude values from
 * the resulting array.
 * @return {Array}
 */
var objectKeys = function objectKeys(target, pred) {
  pred = pred || has;

  var results = [];

  for (var key in target) {
    if (pred(target, key)) {
      results.push(String(key));
    }
  }

  return results;
};

/**
 * Creates an array composed of all keys on the input object. Ignores any non-enumerable properties.
 * More permissive than the native `Object.keys` function (non-objects will not throw errors).
 *
 * @name keys
 * @api public
 * @category Object
 * @param {Object} source The value to retrieve keys from.
 * @return {Array} An array containing all the input `source`'s keys.
 * @example
 * keys({ likes: 'avocado', hates: 'pineapple' });
 * //=> ['likes', 'pineapple'];
 *
 * // Ignores non-enumerable properties
 * var hasHiddenKey = { name: 'Tim' };
 * Object.defineProperty(hasHiddenKey, 'hidden', {
 *   value: 'i am not enumerable!',
 *   enumerable: false
 * })
 * keys(hasHiddenKey);
 * //=> ['name'];
 *
 * // Works on arrays
 * keys(['a', 'b', 'c']);
 * //=> ['0', '1', '2']
 *
 * // Skips unpopulated indices in sparse arrays
 * var arr = [1];
 * arr[4] = 4;
 * keys(arr);
 * //=> ['0', '4']
 */
var keys = function keys(source) {
  if (source == null) {
    return [];
  }

  // IE6-8 compatibility (string)
  if (isString(source)) {
    return indexKeys(source, charAt);
  }

  // IE6-8 compatibility (arguments)
  if (isArrayLike(source)) {
    return indexKeys(source, has);
  }

  return objectKeys(source);
};

/*
 * Exports.
 */

module.exports = keys;

},{}],12:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var each = require('@ndhoule/each');

/**
 * Produce a new array by passing each value in the input `collection` through a transformative
 * `iterator` function. The `iterator` function is passed three arguments:
 * `(value, index, collection)`.
 *
 * @name map
 * @api public
 * @param {Function} iterator The transformer function to invoke per iteration.
 * @param {Array} collection The collection to iterate over.
 * @return {Array} A new array containing the results of each `iterator` invocation.
 * @example
 * var square = function(x) { return x * x; };
 *
 * map(square, [1, 2, 3]);
 * //=> [1, 4, 9]
 */
var map = function map(iterator, collection) {
  if (typeof iterator !== 'function') {
    throw new TypeError('Expected a function but received a ' + typeof iterator);
  }

  var result = [];

  each(function(val, i, collection) {
    result.push(iterator(val, i, collection));
  }, collection);

  return result;
};

/*
 * Exports.
 */

module.exports = map;

},{"@ndhoule/each":6}],13:[function(require,module,exports){
'use strict';

var objToString = Object.prototype.toString;

// TODO: Move to lib
var existy = function(val) {
  return val != null;
};

// TODO: Move to lib
var isArray = function(val) {
  return objToString.call(val) === '[object Array]';
};

// TODO: Move to lib
var isString = function(val) {
   return typeof val === 'string' || objToString.call(val) === '[object String]';
};

// TODO: Move to lib
var isObject = function(val) {
  return val != null && typeof val === 'object';
};

/**
 * Returns a copy of the new `object` containing only the specified properties.
 *
 * @name pick
 * @api public
 * @param {string|string[]} props The property or properties to keep.
 * @param {Object} object The object to iterate over.
 * @return {Object} A new object containing only the specified properties from `object`.
 * @example
 * var person = { name: 'Tim', occupation: 'enchanter', fears: 'rabbits' };
 *
 * pick('name', person);
 * //=> { name: 'Tim' }
 *
 * pick(['name', 'fears'], person);
 * //=> { name: 'Tim', fears: 'rabbits' }
 */
var pick = function pick(props, object) {
  if (!existy(object) || !isObject(object)) {
    return {};
  }

  if (isString(props)) {
    props = [props];
  }

  if (!isArray(props)) {
    props = [];
  }

  var result = {};

  for (var i = 0; i < props.length; i += 1) {
    if (isString(props[i]) && props[i] in object) {
      result[props[i]] = object[props[i]];
    }
  }

  return result;
};

/*
 * Exports.
 */

module.exports = pick;

},{}],14:[function(require,module,exports){
'use strict';

var max = Math.max;

/**
 * Produce a new array by passing each value in the input `collection` through a transformative
 * `iterator` function. The `iterator` function is passed three arguments:
 * `(value, index, collection)`.
 *
 * @name rest
 * @api public
 * @param {Array} collection The collection to iterate over.
 * @return {Array} A new array containing all but the first element from `collection`.
 * @example
 * rest([1, 2, 3]); // => [2, 3]
 */
var rest = function rest(collection) {
  if (collection == null || !collection.length) {
    return [];
  }

  // Preallocating an array *significantly* boosts performance when dealing with
  // `arguments` objects on v8. For a summary, see:
  // https://github.com/petkaantonov/bluebird/wiki/Optimization-killers#32-leaking-arguments
  var results = new Array(max(collection.length - 2, 0));

  for (var i = 1; i < collection.length; i += 1) {
    results[i - 1] = collection[i];
  }

  return results;
};

/*
 * Exports.
 */

module.exports = rest;

},{}],15:[function(require,module,exports){
(function (global){(function (){
'use strict';

var _analytics = global.analytics;

/*
 * Module dependencies.
 */

var Alias = require('segmentio-facade').Alias;
var Emitter = require('component-emitter');
var Facade = require('segmentio-facade');
var Group = require('segmentio-facade').Group;
var Identify = require('segmentio-facade').Identify;
var SourceMiddlewareChain = require('./middleware').SourceMiddlewareChain;
var IntegrationMiddlewareChain = require('./middleware')
  .IntegrationMiddlewareChain;
var Page = require('segmentio-facade').Page;
var Track = require('segmentio-facade').Track;
var bindAll = require('bind-all');
var clone = require('@ndhoule/clone');
var extend = require('extend');
var cookie = require('./cookie');
var metrics = require('./metrics');
var debug = require('debug');
var defaults = require('@ndhoule/defaults');
var each = require('@ndhoule/each');
var foldl = require('@ndhoule/foldl');
var group = require('./group');
var is = require('is');
var isMeta = require('@segment/is-meta');
var keys = require('@ndhoule/keys');
var memory = require('./memory');
var nextTick = require('next-tick');
var normalize = require('./normalize');
var on = require('component-event').bind;
var pageDefaults = require('./pageDefaults');
var pick = require('@ndhoule/pick');
var prevent = require('@segment/prevent-default');
var querystring = require('component-querystring');
var store = require('./store');
var user = require('./user');
var type = require('component-type');

/**
 * Initialize a new `Analytics` instance.
 */

function Analytics() {
  this._options({});
  this.Integrations = {};
  this._sourceMiddlewares = new SourceMiddlewareChain();
  this._integrationMiddlewares = new IntegrationMiddlewareChain();
  this._integrations = {};
  this._readied = false;
  this._timeout = 300;
  // XXX: BACKWARDS COMPATIBILITY
  this._user = user;
  this.log = debug('analytics.js');
  bindAll(this);

  var self = this;
  this.on('initialize', function(settings, options) {
    if (options.initialPageview) self.page();
    self._parseQuery(window.location.search);
  });
}

/**
 * Mix in event emitter.
 */

Emitter(Analytics.prototype);

/**
 * Use a `plugin`.
 *
 * @param {Function} plugin
 * @return {Analytics}
 */

Analytics.prototype.use = function(plugin) {
  plugin(this);
  return this;
};

/**
 * Define a new `Integration`.
 *
 * @param {Function} Integration
 * @return {Analytics}
 */

Analytics.prototype.addIntegration = function(Integration) {
  var name = Integration.prototype.name;
  if (!name) throw new TypeError('attempted to add an invalid integration');
  this.Integrations[name] = Integration;
  return this;
};

/**
 * Define a new `SourceMiddleware`
 *
 * @param {Function} Middleware
 * @return {Analytics}
 */

Analytics.prototype.addSourceMiddleware = function(middleware) {
  if (this.initialized)
    throw new Error(
      'attempted to add a source middleware after initialization'
    );

  this._sourceMiddlewares.add(middleware);
  return this;
};

/**
 * Define a new `IntegrationMiddleware`
 *
 * @param {Function} Middleware
 * @return {Analytics}
 */

Analytics.prototype.addIntegrationMiddleware = function(middleware) {
  if (this.initialized)
    throw new Error(
      'attempted to add an integration middleware after initialization'
    );

  this._integrationMiddlewares.add(middleware);
  return this;
};

/**
 * Initialize with the given integration `settings` and `options`.
 *
 * Aliased to `init` for convenience.
 *
 * @param {Object} [settings={}]
 * @param {Object} [options={}]
 * @return {Analytics}
 */

Analytics.prototype.init = Analytics.prototype.initialize = function(
  settings,
  options
) {
  settings = settings || {};
  options = options || {};

  this._options(options);
  this._readied = false;

  // clean unknown integrations from settings
  var self = this;
  each(function(opts, name) {
    var Integration = self.Integrations[name];
    if (!Integration) delete settings[name];
  }, settings);

  // add integrations
  each(function(opts, name) {
    // Don't load disabled integrations
    if (options.integrations) {
      if (
        options.integrations[name] === false ||
        (options.integrations.All === false && !options.integrations[name])
      ) {
        return;
      }
    }

    var Integration = self.Integrations[name];
    var clonedOpts = {};
    extend(true, clonedOpts, opts); // deep clone opts
    var integration = new Integration(clonedOpts);
    self.log('initialize %o - %o', name, opts);
    self.add(integration);
  }, settings);

  var integrations = this._integrations;

  // load user now that options are set
  user.load();
  group.load();

  // make ready callback
  var readyCallCount = 0;
  var integrationCount = keys(integrations).length;
  var ready = function() {
    readyCallCount++;
    if (readyCallCount >= integrationCount) {
      self._readied = true;
      self.emit('ready');
    }
  };

  // init if no integrations
  if (integrationCount <= 0) {
    ready();
  }

  // initialize integrations, passing ready
  // create a list of any integrations that did not initialize - this will be passed with all events for replay support:
  this.failedInitializations = [];
  var initialPageSkipped = false;
  each(function(integration) {
    if (
      options.initialPageview &&
      integration.options.initialPageview === false
    ) {
      // We've assumed one initial pageview, so make sure we don't count the first page call.
      var page = integration.page;
      integration.page = function() {
        if (initialPageSkipped) {
          return page.apply(this, arguments);
        }
        initialPageSkipped = true;
        return;
      };
    }

    integration.analytics = self;

    integration.once('ready', ready);
    try {
      metrics.increment('analytics_js.integration.invoke', {
        method: 'initialize',
        integration_name: integration.name
      });
      integration.initialize();
    } catch (e) {
      var integrationName = integration.name;
      metrics.increment('analytics_js.integration.invoke.error', {
        method: 'initialize',
        integration_name: integration.name
      });
      self.failedInitializations.push(integrationName);
      self.log('Error initializing %s integration: %o', integrationName, e);
      // Mark integration as ready to prevent blocking of anyone listening to analytics.ready()

      integration.ready();
    }
  }, integrations);

  // backwards compat with angular plugin and used for init logic checks
  this.initialized = true;

  this.emit('initialize', settings, options);
  return this;
};

/**
 * Set the user's `id`.
 *
 * @param {Mixed} id
 */

Analytics.prototype.setAnonymousId = function(id) {
  this.user().anonymousId(id);
  return this;
};

/**
 * Add an integration.
 *
 * @param {Integration} integration
 */

Analytics.prototype.add = function(integration) {
  this._integrations[integration.name] = integration;
  return this;
};

/**
 * Identify a user by optional `id` and `traits`.
 *
 * @param {string} [id=user.id()] User ID.
 * @param {Object} [traits=null] User traits.
 * @param {Object} [options=null]
 * @param {Function} [fn]
 * @return {Analytics}
 */

Analytics.prototype.identify = function(id, traits, options, fn) {
  // Argument reshuffling.
  /* eslint-disable no-unused-expressions, no-sequences */
  if (is.fn(options)) (fn = options), (options = null);
  if (is.fn(traits)) (fn = traits), (options = null), (traits = null);
  if (is.object(id)) (options = traits), (traits = id), (id = user.id());
  /* eslint-enable no-unused-expressions, no-sequences */

  // clone traits before we manipulate so we don't do anything uncouth, and take
  // from `user` so that we carryover anonymous traits
  user.identify(id, traits);

  var msg = this.normalize({
    options: options,
    traits: user.traits(),
    userId: user.id()
  });

  // Add the initialize integrations so the server-side ones can be disabled too
  if (this.options.integrations) {
    defaults(msg.integrations, this.options.integrations);
  }

  this._invoke('identify', new Identify(msg));

  // emit
  this.emit('identify', id, traits, options);
  this._callback(fn);
  return this;
};

/**
 * Return the current user.
 *
 * @return {Object}
 */

Analytics.prototype.user = function() {
  return user;
};

/**
 * Identify a group by optional `id` and `traits`. Or, if no arguments are
 * supplied, return the current group.
 *
 * @param {string} [id=group.id()] Group ID.
 * @param {Object} [traits=null] Group traits.
 * @param {Object} [options=null]
 * @param {Function} [fn]
 * @return {Analytics|Object}
 */

Analytics.prototype.group = function(id, traits, options, fn) {
  /* eslint-disable no-unused-expressions, no-sequences */
  if (!arguments.length) return group;
  if (is.fn(options)) (fn = options), (options = null);
  if (is.fn(traits)) (fn = traits), (options = null), (traits = null);
  if (is.object(id)) (options = traits), (traits = id), (id = group.id());
  /* eslint-enable no-unused-expressions, no-sequences */

  // grab from group again to make sure we're taking from the source
  group.identify(id, traits);

  var msg = this.normalize({
    options: options,
    traits: group.traits(),
    groupId: group.id()
  });

  // Add the initialize integrations so the server-side ones can be disabled too
  if (this.options.integrations) {
    defaults(msg.integrations, this.options.integrations);
  }

  this._invoke('group', new Group(msg));

  this.emit('group', id, traits, options);
  this._callback(fn);
  return this;
};

/**
 * Track an `event` that a user has triggered with optional `properties`.
 *
 * @param {string} event
 * @param {Object} [properties=null]
 * @param {Object} [options=null]
 * @param {Function} [fn]
 * @return {Analytics}
 */

Analytics.prototype.track = function(event, properties, options, fn) {
  // Argument reshuffling.
  /* eslint-disable no-unused-expressions, no-sequences */
  if (is.fn(options)) (fn = options), (options = null);
  if (is.fn(properties))
    (fn = properties), (options = null), (properties = null);
  /* eslint-enable no-unused-expressions, no-sequences */

  // figure out if the event is archived.
  var plan = this.options.plan || {};
  var events = plan.track || {};
  var planIntegrationOptions = {};

  // normalize
  var msg = this.normalize({
    properties: properties,
    options: options,
    event: event
  });

  // plan.
  plan = events[event];
  if (plan) {
    this.log('plan %o - %o', event, plan);
    if (plan.enabled === false) {
      // Disabled events should always be sent to Segment.
      planIntegrationOptions = { All: false, 'Segment.io': true };
    } else {
      planIntegrationOptions = plan.integrations || {};
    }
  } else {
    var defaultPlan = events.__default || { enabled: true };
    if (!defaultPlan.enabled) {
      // Disabled events should always be sent to Segment.
      planIntegrationOptions = { All: false, 'Segment.io': true };
    }
  }

  // Add the initialize integrations so the server-side ones can be disabled too
  defaults(
    msg.integrations,
    this._mergeInitializeAndPlanIntegrations(planIntegrationOptions)
  );

  this._invoke('track', new Track(msg));

  this.emit('track', event, properties, options);
  this._callback(fn);
  return this;
};

/**
 * Helper method to track an outbound link that would normally navigate away
 * from the page before the analytics calls were sent.
 *
 * BACKWARDS COMPATIBILITY: aliased to `trackClick`.
 *
 * @param {Element|Array} links
 * @param {string|Function} event
 * @param {Object|Function} properties (optional)
 * @return {Analytics}
 */

Analytics.prototype.trackClick = Analytics.prototype.trackLink = function(
  links,
  event,
  properties
) {
  if (!links) return this;
  // always arrays, handles jquery
  if (type(links) === 'element') links = [links];

  var self = this;
  each(function(el) {
    if (type(el) !== 'element') {
      throw new TypeError('Must pass HTMLElement to `analytics.trackLink`.');
    }
    on(el, 'click', function(e) {
      var ev = is.fn(event) ? event(el) : event;
      var props = is.fn(properties) ? properties(el) : properties;
      var href =
        el.getAttribute('href') ||
        el.getAttributeNS('http://www.w3.org/1999/xlink', 'href') ||
        el.getAttribute('xlink:href');

      self.track(ev, props);

      if (href && el.target !== '_blank' && !isMeta(e)) {
        prevent(e);
        self._callback(function() {
          window.location.href = href;
        });
      }
    });
  }, links);

  return this;
};

/**
 * Helper method to track an outbound form that would normally navigate away
 * from the page before the analytics calls were sent.
 *
 * BACKWARDS COMPATIBILITY: aliased to `trackSubmit`.
 *
 * @param {Element|Array} forms
 * @param {string|Function} event
 * @param {Object|Function} properties (optional)
 * @return {Analytics}
 */

Analytics.prototype.trackSubmit = Analytics.prototype.trackForm = function(
  forms,
  event,
  properties
) {
  if (!forms) return this;
  // always arrays, handles jquery
  if (type(forms) === 'element') forms = [forms];

  var self = this;
  each(function(el) {
    if (type(el) !== 'element')
      throw new TypeError('Must pass HTMLElement to `analytics.trackForm`.');
    function handler(e) {
      prevent(e);

      var ev = is.fn(event) ? event(el) : event;
      var props = is.fn(properties) ? properties(el) : properties;
      self.track(ev, props);

      self._callback(function() {
        el.submit();
      });
    }

    // Support the events happening through jQuery or Zepto instead of through
    // the normal DOM API, because `el.submit` doesn't bubble up events...
    var $ = window.jQuery || window.Zepto;
    if ($) {
      $(el).submit(handler);
    } else {
      on(el, 'submit', handler);
    }
  }, forms);

  return this;
};

/**
 * Trigger a pageview, labeling the current page with an optional `category`,
 * `name` and `properties`.
 *
 * @param {string} [category]
 * @param {string} [name]
 * @param {Object|string} [properties] (or path)
 * @param {Object} [options]
 * @param {Function} [fn]
 * @return {Analytics}
 */

Analytics.prototype.page = function(category, name, properties, options, fn) {
  // Argument reshuffling.
  /* eslint-disable no-unused-expressions, no-sequences */
  if (is.fn(options)) (fn = options), (options = null);
  if (is.fn(properties)) (fn = properties), (options = properties = null);
  if (is.fn(name)) (fn = name), (options = properties = name = null);
  if (type(category) === 'object')
    (options = name), (properties = category), (name = category = null);
  if (type(name) === 'object')
    (options = properties), (properties = name), (name = null);
  if (type(category) === 'string' && type(name) !== 'string')
    (name = category), (category = null);
  /* eslint-enable no-unused-expressions, no-sequences */

  properties = clone(properties) || {};
  if (name) properties.name = name;
  if (category) properties.category = category;

  // Ensure properties has baseline spec properties.
  // TODO: Eventually move these entirely to `options.context.page`
  var defs = pageDefaults();
  defaults(properties, defs);

  // Mirror user overrides to `options.context.page` (but exclude custom properties)
  // (Any page defaults get applied in `this.normalize` for consistency.)
  // Weird, yeah--moving special props to `context.page` will fix this in the long term.
  var overrides = pick(keys(defs), properties);
  if (!is.empty(overrides)) {
    options = options || {};
    options.context = options.context || {};
    options.context.page = overrides;
  }

  var msg = this.normalize({
    properties: properties,
    category: category,
    options: options,
    name: name
  });

  // Add the initialize integrations so the server-side ones can be disabled too
  if (this.options.integrations) {
    defaults(msg.integrations, this.options.integrations);
  }

  this._invoke('page', new Page(msg));

  this.emit('page', category, name, properties, options);
  this._callback(fn);
  return this;
};

/**
 * FIXME: BACKWARDS COMPATIBILITY: convert an old `pageview` to a `page` call.
 *
 * @param {string} [url]
 * @return {Analytics}
 * @api private
 */

Analytics.prototype.pageview = function(url) {
  var properties = {};
  if (url) properties.path = url;
  this.page(properties);
  return this;
};

/**
 * Merge two previously unassociated user identities.
 *
 * @param {string} to
 * @param {string} from (optional)
 * @param {Object} options (optional)
 * @param {Function} fn (optional)
 * @return {Analytics}
 */

Analytics.prototype.alias = function(to, from, options, fn) {
  // Argument reshuffling.
  /* eslint-disable no-unused-expressions, no-sequences */
  if (is.fn(options)) (fn = options), (options = null);
  if (is.fn(from)) (fn = from), (options = null), (from = null);
  if (is.object(from)) (options = from), (from = null);
  /* eslint-enable no-unused-expressions, no-sequences */

  var msg = this.normalize({
    options: options,
    previousId: from,
    userId: to
  });

  // Add the initialize integrations so the server-side ones can be disabled too
  if (this.options.integrations) {
    defaults(msg.integrations, this.options.integrations);
  }

  this._invoke('alias', new Alias(msg));

  this.emit('alias', to, from, options);
  this._callback(fn);
  return this;
};

/**
 * Register a `fn` to be fired when all the analytics services are ready.
 *
 * @param {Function} fn
 * @return {Analytics}
 */

Analytics.prototype.ready = function(fn) {
  if (is.fn(fn)) {
    if (this._readied) {
      nextTick(fn);
    } else {
      this.once('ready', fn);
    }
  }
  return this;
};

/**
 * Set the `timeout` (in milliseconds) used for callbacks.
 *
 * @param {Number} timeout
 */

Analytics.prototype.timeout = function(timeout) {
  this._timeout = timeout;
};

/**
 * Enable or disable debug.
 *
 * @param {string|boolean} str
 */

Analytics.prototype.debug = function(str) {
  if (!arguments.length || str) {
    debug.enable('analytics:' + (str || '*'));
  } else {
    debug.disable();
  }
};

/**
 * Apply options.
 *
 * @param {Object} options
 * @return {Analytics}
 * @api private
 */

Analytics.prototype._options = function(options) {
  options = options || {};
  this.options = options;
  cookie.options(options.cookie);
  metrics.options(options.metrics);
  store.options(options.localStorage);
  user.options(options.user);
  group.options(options.group);
  return this;
};

/**
 * Callback a `fn` after our defined timeout period.
 *
 * @param {Function} fn
 * @return {Analytics}
 * @api private
 */

Analytics.prototype._callback = function(fn) {
  if (is.fn(fn)) {
    this._timeout ? setTimeout(fn, this._timeout) : nextTick(fn);
  }
  return this;
};

/**
 * Call `method` with `facade` on all enabled integrations.
 *
 * @param {string} method
 * @param {Facade} facade
 * @return {Analytics}
 * @api private
 */

Analytics.prototype._invoke = function(method, facade) {
  var self = this;

  try {
    this._sourceMiddlewares.applyMiddlewares(
      extend(true, new Facade({}), facade),
      this._integrations,
      function(result) {
        // A nullified payload should not be sent.
        if (result === null) {
          self.log(
            'Payload with method "%s" was null and dropped by source a middleware.',
            method
          );
          return;
        }

        // Check if the payload is still a Facade. If not, convert it to one.
        if (!(result instanceof Facade)) {
          result = new Facade(result);
        }

        self.emit('invoke', result);
        metrics.increment('analytics_js.invoke', {
          method: method
        });

        applyIntegrationMiddlewares(result);
      }
    );
  } catch (e) {
    metrics.increment('analytics_js.invoke.error', {
      method: method
    });
    self.log(
      'Error invoking .%s method of %s integration: %o',
      method,
      name,
      e
    );
  }

  return this;

  function applyIntegrationMiddlewares(facade) {
    var failedInitializations = self.failedInitializations || [];
    each(function(integration, name) {
      var facadeCopy = extend(true, new Facade({}), facade);

      if (!facadeCopy.enabled(name)) return;
      // Check if an integration failed to initialize.
      // If so, do not process the message as the integration is in an unstable state.
      if (failedInitializations.indexOf(name) >= 0) {
        self.log(
          'Skipping invocation of .%s method of %s integration. Integration failed to initialize properly.',
          method,
          name
        );
      } else {
        try {
          // Apply any integration middlewares that exist, then invoke the integration with the result.
          self._integrationMiddlewares.applyMiddlewares(
            facadeCopy,
            integration.name,
            function(result) {
              // A nullified payload should not be sent to an integration.
              if (result === null) {
                self.log(
                  'Payload to integration "%s" was null and dropped by a middleware.',
                  name
                );
                return;
              }

              // Check if the payload is still a Facade. If not, convert it to one.
              if (!(result instanceof Facade)) {
                result = new Facade(result);
              }

              metrics.increment('analytics_js.integration.invoke', {
                method: method,
                integration_name: integration.name
              });

              integration.invoke.call(integration, method, result);
            }
          );
        } catch (e) {
          metrics.increment('analytics_js.integration.invoke.error', {
            method: method,
            integration_name: integration.name
          });
          self.log(
            'Error invoking .%s method of %s integration: %o',
            method,
            name,
            e
          );
        }
      }
    }, self._integrations);
  }
};

/**
 * Push `args`.
 *
 * @param {Array} args
 * @api private
 */

Analytics.prototype.push = function(args) {
  var method = args.shift();
  if (!this[method]) return;
  this[method].apply(this, args);
};

/**
 * Reset group and user traits and id's.
 *
 * @api public
 */

Analytics.prototype.reset = function() {
  this.user().logout();
  this.group().logout();
};

/**
 * Parse the query string for callable methods.
 *
 * @param {String} query
 * @return {Analytics}
 * @api private
 */

Analytics.prototype._parseQuery = function(query) {
  // Parse querystring to an object
  var q = querystring.parse(query);
  // Create traits and properties objects, populate from querysting params
  var traits = pickPrefix('ajs_trait_', q);
  var props = pickPrefix('ajs_prop_', q);
  // Trigger based on callable parameters in the URL
  if (q.ajs_uid) this.identify(q.ajs_uid, traits);
  if (q.ajs_event) this.track(q.ajs_event, props);
  if (q.ajs_aid) user.anonymousId(q.ajs_aid);
  return this;

  /**
   * Create a shallow copy of an input object containing only the properties
   * whose keys are specified by a prefix, stripped of that prefix
   *
   * @param {String} prefix
   * @param {Object} object
   * @return {Object}
   * @api private
   */

  function pickPrefix(prefix, object) {
    var length = prefix.length;
    var sub;
    return foldl(
      function(acc, val, key) {
        if (key.substr(0, length) === prefix) {
          sub = key.substr(length);
          acc[sub] = val;
        }
        return acc;
      },
      {},
      object
    );
  }
};

/**
 * Normalize the given `msg`.
 *
 * @param {Object} msg
 * @return {Object}
 */

Analytics.prototype.normalize = function(msg) {
  msg = normalize(msg, keys(this._integrations));
  if (msg.anonymousId) user.anonymousId(msg.anonymousId);
  msg.anonymousId = user.anonymousId();

  // Ensure all outgoing requests include page data in their contexts.
  msg.context.page = defaults(msg.context.page || {}, pageDefaults());

  return msg;
};

/**
 * Merges the tracking plan and initialization integration options.
 *
 * @param  {Object} planIntegrations Tracking plan integrations.
 * @return {Object}                  The merged integrations.
 */
Analytics.prototype._mergeInitializeAndPlanIntegrations = function(
  planIntegrations
) {
  // Do nothing if there are no initialization integrations
  if (!this.options.integrations) {
    return planIntegrations;
  }

  // Clone the initialization integrations
  var integrations = extend({}, this.options.integrations);
  var integrationName;

  // Allow the tracking plan to disable integrations that were explicitly
  // enabled on initialization
  if (planIntegrations.All === false) {
    integrations = { All: false };
  }

  for (integrationName in planIntegrations) {
    if (planIntegrations.hasOwnProperty(integrationName)) {
      // Don't allow the tracking plan to re-enable disabled integrations
      if (this.options.integrations[integrationName] !== false) {
        integrations[integrationName] = planIntegrations[integrationName];
      }
    }
  }

  return integrations;
};

/**
 * No conflict support.
 */

Analytics.prototype.noConflict = function() {
  window.analytics = _analytics;
  return this;
};

/*
 * Exports.
 */

module.exports = Analytics;
module.exports.cookie = cookie;
module.exports.memory = memory;
module.exports.store = store;
module.exports.metrics = metrics;

}).call(this)}).call(this,typeof global !== "undefined" ? global : typeof self !== "undefined" ? self : typeof window !== "undefined" ? window : {})
},{"./cookie":16,"./group":18,"./memory":20,"./metrics":21,"./middleware":22,"./normalize":23,"./pageDefaults":24,"./store":25,"./user":26,"@ndhoule/clone":3,"@ndhoule/defaults":4,"@ndhoule/each":6,"@ndhoule/foldl":9,"@ndhoule/keys":11,"@ndhoule/pick":13,"@segment/is-meta":35,"@segment/prevent-default":39,"bind-all":44,"component-emitter":52,"component-event":53,"component-querystring":55,"component-type":57,"debug":27,"extend":62,"is":66,"next-tick":76,"segmentio-facade":86}],16:[function(require,module,exports){
'use strict';

/**
 * Module dependencies.
 */

var bindAll = require('bind-all');
var clone = require('@ndhoule/clone');
var cookie = require('component-cookie');
var debug = require('debug')('analytics.js:cookie');
var defaults = require('@ndhoule/defaults');
var json = require('json3');
var topDomain = require('@segment/top-domain');

/**
 * Initialize a new `Cookie` with `options`.
 *
 * @param {Object} options
 */

function Cookie(options) {
  this.options(options);
}

/**
 * Get or set the cookie options.
 *
 * @param {Object} options
 *   @field {Number} maxage (1 year)
 *   @field {String} domain
 *   @field {String} path
 *   @field {Boolean} secure
 */

Cookie.prototype.options = function(options) {
  if (arguments.length === 0) return this._options;

  options = options || {};

  var domain = '.' + topDomain(window.location.href);
  if (domain === '.') domain = null;

  this._options = defaults(options, {
    // default to a year
    maxage: 31536000000,
    path: '/',
    domain: domain
  });

  // http://curl.haxx.se/rfc/cookie_spec.html
  // https://publicsuffix.org/list/effective_tld_names.dat
  //
  // try setting a dummy cookie with the options
  // if the cookie isn't set, it probably means
  // that the domain is on the public suffix list
  // like myapp.herokuapp.com or localhost / ip.
  this.set('ajs:test', true);
  if (!this.get('ajs:test')) {
    debug('fallback to domain=null');
    this._options.domain = null;
  }
  this.remove('ajs:test');
};

/**
 * Set a `key` and `value` in our cookie.
 *
 * @param {String} key
 * @param {Object} value
 * @return {Boolean} saved
 */

Cookie.prototype.set = function(key, value) {
  try {
    value = json.stringify(value);
    cookie(key, value, clone(this._options));
    return true;
  } catch (e) {
    return false;
  }
};

/**
 * Get a value from our cookie by `key`.
 *
 * @param {String} key
 * @return {Object} value
 */

Cookie.prototype.get = function(key) {
  try {
    var value = cookie(key);
    value = value ? json.parse(value) : null;
    return value;
  } catch (e) {
    return null;
  }
};

/**
 * Remove a value from our cookie by `key`.
 *
 * @param {String} key
 * @return {Boolean} removed
 */

Cookie.prototype.remove = function(key) {
  try {
    cookie(key, null, clone(this._options));
    return true;
  } catch (e) {
    return false;
  }
};

/**
 * Expose the cookie singleton.
 */

module.exports = bindAll(new Cookie());

/**
 * Expose the `Cookie` constructor.
 */

module.exports.Cookie = Cookie;

},{"@ndhoule/clone":3,"@ndhoule/defaults":4,"@segment/top-domain":42,"bind-all":44,"component-cookie":46,"debug":27,"json3":67}],17:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var clone = require('@ndhoule/clone');
var cookie = require('./cookie');
var debug = require('debug')('analytics:entity');
var defaults = require('@ndhoule/defaults');
var extend = require('@ndhoule/extend');
var memory = require('./memory');
var store = require('./store');
var isodateTraverse = require('@segment/isodate-traverse');

/**
 * Expose `Entity`
 */

module.exports = Entity;

/**
 * Initialize new `Entity` with `options`.
 *
 * @param {Object} options
 */

function Entity(options) {
  this.options(options);
  this.initialize();
}

/**
 * Initialize picks the storage.
 *
 * Checks to see if cookies can be set
 * otherwise fallsback to localStorage.
 */

Entity.prototype.initialize = function() {
  cookie.set('ajs:cookies', true);

  // cookies are enabled.
  if (cookie.get('ajs:cookies')) {
    cookie.remove('ajs:cookies');
    this._storage = cookie;
    return;
  }

  // localStorage is enabled.
  if (store.enabled) {
    this._storage = store;
    return;
  }

  // fallback to memory storage.
  debug(
    'warning using memory store both cookies and localStorage are disabled'
  );
  this._storage = memory;
};

/**
 * Get the storage.
 */

Entity.prototype.storage = function() {
  return this._storage;
};

/**
 * Get or set storage `options`.
 *
 * @param {Object} options
 *   @property {Object} cookie
 *   @property {Object} localStorage
 *   @property {Boolean} persist (default: `true`)
 */

Entity.prototype.options = function(options) {
  if (arguments.length === 0) return this._options;
  this._options = defaults(options || {}, this.defaults || {});
};

/**
 * Get or set the entity's `id`.
 *
 * @param {String} id
 */

Entity.prototype.id = function(id) {
  switch (arguments.length) {
    case 0:
      return this._getId();
    case 1:
      return this._setId(id);
    default:
    // No default case
  }
};

/**
 * Get the entity's id.
 *
 * @return {String}
 */

Entity.prototype._getId = function() {
  if (!this._options.persist) {
    return this._id === undefined ? null : this._id;
  }

  // Check cookies.
  var cookieId = this._getIdFromCookie();
  if (cookieId) {
    return cookieId;
  }

  // Check localStorage.
  var lsId = this._getIdFromLocalStorage();
  if (lsId) {
    // Copy the id to cookies so we can read it directly from cookies next time.
    this._setIdInCookies(lsId);
    return lsId;
  }

  return null;
};

/**
 * Get the entity's id from cookies.
 *
 * @return {String}
 */

Entity.prototype._getIdFromCookie = function() {
  return this.storage().get(this._options.cookie.key);
};

/**
 * Get the entity's id from cookies.
 *
 * @return {String}
 */

Entity.prototype._getIdFromLocalStorage = function() {
  if (!this._options.localStorageFallbackDisabled) {
    return store.get(this._options.cookie.key);
  }
  return null;
};

/**
 * Set the entity's `id`.
 *
 * @param {String} id
 */

Entity.prototype._setId = function(id) {
  if (this._options.persist) {
    this._setIdInCookies(id);
    this._setIdInLocalStorage(id);
  } else {
    this._id = id;
  }
};

/**
 * Set the entity's `id` in cookies.
 *
 * @param {String} id
 */

Entity.prototype._setIdInCookies = function(id) {
  this.storage().set(this._options.cookie.key, id);
};

/**
 * Set the entity's `id` in local storage.
 *
 * @param {String} id
 */

Entity.prototype._setIdInLocalStorage = function(id) {
  if (!this._options.localStorageFallbackDisabled) {
    store.set(this._options.cookie.key, id);
  }
};

/**
 * Get or set the entity's `traits`.
 *
 * BACKWARDS COMPATIBILITY: aliased to `properties`
 *
 * @param {Object} traits
 */

Entity.prototype.properties = Entity.prototype.traits = function(traits) {
  switch (arguments.length) {
    case 0:
      return this._getTraits();
    case 1:
      return this._setTraits(traits);
    default:
    // No default case
  }
};

/**
 * Get the entity's traits. Always convert ISO date strings into real dates,
 * since they aren't parsed back from local storage.
 *
 * @return {Object}
 */

Entity.prototype._getTraits = function() {
  var ret = this._options.persist
    ? store.get(this._options.localStorage.key)
    : this._traits;
  return ret ? isodateTraverse(clone(ret)) : {};
};

/**
 * Set the entity's `traits`.
 *
 * @param {Object} traits
 */

Entity.prototype._setTraits = function(traits) {
  traits = traits || {};
  if (this._options.persist) {
    store.set(this._options.localStorage.key, traits);
  } else {
    this._traits = traits;
  }
};

/**
 * Identify the entity with an `id` and `traits`. If we it's the same entity,
 * extend the existing `traits` instead of overwriting.
 *
 * @param {String} id
 * @param {Object} traits
 */

Entity.prototype.identify = function(id, traits) {
  traits = traits || {};
  var current = this.id();
  if (current === null || current === id)
    traits = extend(this.traits(), traits);
  if (id) this.id(id);
  this.debug('identify %o, %o', id, traits);
  this.traits(traits);
  this.save();
};

/**
 * Save the entity to local storage and the cookie.
 *
 * @return {Boolean}
 */

Entity.prototype.save = function() {
  if (!this._options.persist) return false;
  this._setId(this.id());
  this._setTraits(this.traits());
  return true;
};

/**
 * Log the entity out, reseting `id` and `traits` to defaults.
 */

Entity.prototype.logout = function() {
  this.id(null);
  this.traits({});
  this.storage().remove(this._options.cookie.key);
  store.remove(this._options.cookie.key);
  store.remove(this._options.localStorage.key);
};

/**
 * Reset all entity state, logging out and returning options to defaults.
 */

Entity.prototype.reset = function() {
  this.logout();
  this.options({});
};

/**
 * Load saved entity `id` or `traits` from storage.
 */

Entity.prototype.load = function() {
  this.id(this.id());
  this.traits(this.traits());
};

},{"./cookie":16,"./memory":20,"./store":25,"@ndhoule/clone":3,"@ndhoule/defaults":4,"@ndhoule/extend":8,"@segment/isodate-traverse":36,"debug":27}],18:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var Entity = require('./entity');
var bindAll = require('bind-all');
var debug = require('debug')('analytics:group');
var inherit = require('inherits');

/**
 * Group defaults
 */

Group.defaults = {
  persist: true,
  cookie: {
    key: 'ajs_group_id'
  },
  localStorage: {
    key: 'ajs_group_properties'
  }
};

/**
 * Initialize a new `Group` with `options`.
 *
 * @param {Object} options
 */

function Group(options) {
  this.defaults = Group.defaults;
  this.debug = debug;
  Entity.call(this, options);
}

/**
 * Inherit `Entity`
 */

inherit(Group, Entity);

/**
 * Expose the group singleton.
 */

module.exports = bindAll(new Group());

/**
 * Expose the `Group` constructor.
 */

module.exports.Group = Group;

},{"./entity":17,"bind-all":44,"debug":27,"inherits":64}],19:[function(require,module,exports){
'use strict';

/**
 * Analytics.js
 *
 * (C) 2013-2016 Segment.io Inc.
 */

var Analytics = require('./analytics');

// Create a new `analytics` singleton.
var analytics = new Analytics();

// Expose `require`.
// TODO(ndhoule): Look into deprecating, we no longer need to expose it in tests
//analytics.require = require;

// Expose package version.
analytics.VERSION = require('../package.json').version;

/*
 * Exports.
 */

module.exports = analytics;

},{"../package.json":28,"./analytics":15}],20:[function(require,module,exports){
'use strict';

/*
 * Module Dependencies.
 */

var bindAll = require('bind-all');
var clone = require('@ndhoule/clone');

/**
 * HOP.
 */

var has = Object.prototype.hasOwnProperty;

/**
 * Expose `Memory`
 */

module.exports = bindAll(new Memory());

/**
 * Initialize `Memory` store
 */

function Memory() {
  this.store = {};
}

/**
 * Set a `key` and `value`.
 *
 * @param {String} key
 * @param {Mixed} value
 * @return {Boolean}
 */

Memory.prototype.set = function(key, value) {
  this.store[key] = clone(value);
  return true;
};

/**
 * Get a `key`.
 *
 * @param {String} key
 */

Memory.prototype.get = function(key) {
  if (!has.call(this.store, key)) return;
  return clone(this.store[key]);
};

/**
 * Remove a `key`.
 *
 * @param {String} key
 * @return {Boolean}
 */

Memory.prototype.remove = function(key) {
  delete this.store[key];
  return true;
};

},{"@ndhoule/clone":3,"bind-all":44}],21:[function(require,module,exports){
'use strict';

var bindAll = require('bind-all');
var send = require('@segment/send-json');
var debug = require('debug')('analytics.js:metrics');

function Metrics(options) {
  this.options(options);
}

/**
 * Set the metrics options.
 *
 * @param {Object} options
 *   @field {String} host
 *   @field {Number} sampleRate
 *   @field {Number} flushTimer
 */

Metrics.prototype.options = function(options) {
  options = options || {};

  this.host = options.host || 'api.segment.io/v1';
  this.sampleRate = options.sampleRate || 0; // disable metrics by default.
  this.flushTimer = options.flushTimer || 30 * 1000 /* 30s */;
  this.maxQueueSize = options.maxQueueSize || 20;

  this.queue = [];

  if (this.sampleRate > 0) {
    var self = this;
    setInterval(function() {
      self._flush();
    }, this.flushTimer);
  }
};

/**
 * Increments the counter identified by name and tags by one.
 *
 * @param {String} metric Name of the metric to increment.
 * @param {Object} tags Dimensions associated with the metric.
 */
Metrics.prototype.increment = function(metric, tags) {
  if (Math.random() > this.sampleRate) {
    return;
  }

  if (this.queue.length >= this.maxQueueSize) {
    return;
  }

  this.queue.push({ type: 'Counter', metric: metric, value: 1, tags: tags });

  // Trigger a flush if this is an error metric.
  if (metric.indexOf('error') > 0) {
    this._flush();
  }
};

/**
 * Flush all queued metrics.
 */
Metrics.prototype._flush = function() {
  var self = this;

  if (self.queue.length <= 0) {
    return;
  }

  var payload = { series: this.queue };
  var headers = { 'Content-Type': 'text/plain' };

  self.queue = [];

  // This endpoint does not support jsonp, so only proceed if the browser
  // supports xhr.
  if (send.type !== 'xhr') return;

  send('https://' + this.host + '/m', payload, headers, function(err, res) {
    debug('sent %O, received %O', payload, [err, res]);
  });
};

/**
 * Expose the metrics singleton.
 */

module.exports = bindAll(new Metrics());

/**
 * Expose the `Metrics` constructor.
 */

module.exports.Metrics = Metrics;

},{"@segment/send-json":40,"bind-all":44,"debug":27}],22:[function(require,module,exports){
'use strict';

var Facade = require('segmentio-facade');

module.exports.SourceMiddlewareChain = function SourceMiddlewareChain() {
  var apply = middlewareChain(this);

  this.applyMiddlewares = function(facade, integrations, callback) {
    return apply(
      function(mw, payload, next) {
        mw({
          integrations: integrations,
          next: next,
          payload: payload
        });
      },
      facade,
      callback
    );
  };
};

module.exports.IntegrationMiddlewareChain = function IntegrationMiddlewareChain() {
  var apply = middlewareChain(this);

  this.applyMiddlewares = function(facade, integration, callback) {
    return apply(
      function(mw, payload, next) {
        mw(payload, integration, next);
      },
      facade,
      callback
    );
  };
};

// Chain is essentially a linked list of middlewares to run in order.
function middlewareChain(dest) {
  var middlewares = [];

  // Return a copy to prevent external mutations.
  dest.getMiddlewares = function() {
    return middlewares.slice();
  };

  dest.add = function(middleware) {
    if (typeof middleware !== 'function')
      throw new Error('attempted to add non-function middleware');

    // Check for identical object references - bug check.
    if (middlewares.indexOf(middleware) !== -1)
      throw new Error('middleware is already registered');
    middlewares.push(middleware);
  };

  // fn is the callback to be run once all middlewares have been applied.
  return function applyMiddlewares(run, facade, callback) {
    if (typeof facade !== 'object')
      throw new Error('applyMiddlewares requires a payload object');
    if (typeof callback !== 'function')
      throw new Error('applyMiddlewares requires a function callback');

    // Attach callback to the end of the chain.
    var middlewaresToApply = middlewares.slice();
    middlewaresToApply.push(callback);
    executeChain(run, facade, middlewaresToApply, 0);
  };
}

// Go over all middlewares until all have been applied.
function executeChain(run, payload, middlewares, index) {
  // If the facade has been nullified, immediately skip to the final middleware.
  if (payload === null) {
    middlewares[middlewares.length - 1](null);
    return;
  }

  // Check if the payload is still a Facade. If not, convert it to one.
  if (!(payload instanceof Facade)) {
    payload = new Facade(payload);
  }

  var mw = middlewares[index];
  if (mw) {
    // If there's another middleware, continue down the chain. Otherwise, call the final function.
    if (middlewares[index + 1]) {
      run(mw, payload, function(result) {
        executeChain(run, result, middlewares, ++index);
      });
    } else {
      mw(payload);
    }
  }
}

module.exports.middlewareChain = middlewareChain;

},{"segmentio-facade":86}],23:[function(require,module,exports){
'use strict';

/**
 * Module Dependencies.
 */

var debug = require('debug')('analytics.js:normalize');
var defaults = require('@ndhoule/defaults');
var each = require('@ndhoule/each');
var includes = require('@ndhoule/includes');
var map = require('@ndhoule/map');
var type = require('component-type');
var uuid = require('uuid').v4;
var json = require('json3');
var md5 = require('spark-md5').hash;

/**
 * HOP.
 */

var has = Object.prototype.hasOwnProperty;

/**
 * Expose `normalize`
 */

module.exports = normalize;

/**
 * Toplevel properties.
 */

var toplevel = ['integrations', 'anonymousId', 'timestamp', 'context'];

/**
 * Normalize `msg` based on integrations `list`.
 *
 * @param {Object} msg
 * @param {Array} list
 * @return {Function}
 */

function normalize(msg, list) {
  var lower = map(function(s) {
    return s.toLowerCase();
  }, list);
  var opts = msg.options || {};
  var integrations = opts.integrations || {};
  var providers = opts.providers || {};
  var context = opts.context || {};
  var ret = {};
  debug('<-', msg);

  // integrations.
  each(function(value, key) {
    if (!integration(key)) return;
    if (!has.call(integrations, key)) integrations[key] = value;
    delete opts[key];
  }, opts);

  // providers.
  delete opts.providers;
  each(function(value, key) {
    if (!integration(key)) return;
    if (type(integrations[key]) === 'object') return;
    if (has.call(integrations, key) && typeof providers[key] === 'boolean')
      return;
    integrations[key] = value;
  }, providers);

  // move all toplevel options to msg
  // and the rest to context.
  each(function(value, key) {
    if (includes(key, toplevel)) {
      ret[key] = opts[key];
    } else {
      context[key] = opts[key];
    }
  }, opts);

  // generate and attach a messageId to msg
  msg.messageId = 'ajs-' + md5(json.stringify(msg) + uuid());

  // cleanup
  delete msg.options;
  ret.integrations = integrations;
  ret.context = context;
  ret = defaults(ret, msg);
  debug('->', ret);
  return ret;

  function integration(name) {
    return !!(
      includes(name, list) ||
      name.toLowerCase() === 'all' ||
      includes(name.toLowerCase(), lower)
    );
  }
}

},{"@ndhoule/defaults":4,"@ndhoule/each":6,"@ndhoule/includes":10,"@ndhoule/map":12,"component-type":57,"debug":27,"json3":67,"spark-md5":93,"uuid":101}],24:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var canonical = require('@segment/canonical');
var includes = require('@ndhoule/includes');
var url = require('component-url');

/**
 * Return a default `options.context.page` object.
 *
 * https://segment.com/docs/spec/page/#properties
 *
 * @return {Object}
 */

function pageDefaults() {
  return {
    path: canonicalPath(),
    referrer: document.referrer,
    search: location.search,
    title: document.title,
    url: canonicalUrl(location.search)
  };
}

/**
 * Return the canonical path for the page.
 *
 * @return {string}
 */

function canonicalPath() {
  var canon = canonical();
  if (!canon) return window.location.pathname;
  var parsed = url.parse(canon);
  return parsed.pathname;
}

/**
 * Return the canonical URL for the page concat the given `search`
 * and strip the hash.
 *
 * @param {string} search
 * @return {string}
 */

function canonicalUrl(search) {
  var canon = canonical();
  if (canon) return includes('?', canon) ? canon : canon + search;
  var url = window.location.href;
  var i = url.indexOf('#');
  return i === -1 ? url : url.slice(0, i);
}

/*
 * Exports.
 */

module.exports = pageDefaults;

},{"@ndhoule/includes":10,"@segment/canonical":33,"component-url":58}],25:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var bindAll = require('bind-all');
var defaults = require('@ndhoule/defaults');
var store = require('@segment/store');

/**
 * Initialize a new `Store` with `options`.
 *
 * @param {Object} options
 */

function Store(options) {
  this.options(options);
}

/**
 * Set the `options` for the store.
 *
 * @param {Object} options
 *   @field {Boolean} enabled (true)
 */

Store.prototype.options = function(options) {
  if (arguments.length === 0) return this._options;

  options = options || {};
  defaults(options, { enabled: true });

  this.enabled = options.enabled && store.enabled;
  this._options = options;
};

/**
 * Set a `key` and `value` in local storage.
 *
 * @param {string} key
 * @param {Object} value
 */

Store.prototype.set = function(key, value) {
  if (!this.enabled) return false;
  return store.set(key, value);
};

/**
 * Get a value from local storage by `key`.
 *
 * @param {string} key
 * @return {Object}
 */

Store.prototype.get = function(key) {
  if (!this.enabled) return null;
  return store.get(key);
};

/**
 * Remove a value from local storage by `key`.
 *
 * @param {string} key
 */

Store.prototype.remove = function(key) {
  if (!this.enabled) return false;
  return store.remove(key);
};

/**
 * Expose the store singleton.
 */

module.exports = bindAll(new Store());

/**
 * Expose the `Store` constructor.
 */

module.exports.Store = Store;

},{"@ndhoule/defaults":4,"@segment/store":41,"bind-all":44}],26:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var Entity = require('./entity');
var bindAll = require('bind-all');
var cookie = require('./cookie');
var debug = require('debug')('analytics:user');
var inherit = require('inherits');
var rawCookie = require('component-cookie');
var uuid = require('uuid');
var localStorage = require('./store');

/**
 * User defaults
 */

User.defaults = {
  persist: true,
  cookie: {
    key: 'ajs_user_id',
    oldKey: 'ajs_user'
  },
  localStorage: {
    key: 'ajs_user_traits'
  }
};

/**
 * Initialize a new `User` with `options`.
 *
 * @param {Object} options
 */

function User(options) {
  this.defaults = User.defaults;
  this.debug = debug;
  Entity.call(this, options);
}

/**
 * Inherit `Entity`
 */

inherit(User, Entity);

/**
 * Set/get the user id.
 *
 * When the user id changes, the method will reset his anonymousId to a new one.
 *
 * // FIXME: What are the mixed types?
 * @param {string} id
 * @return {Mixed}
 * @example
 * // didn't change because the user didn't have previous id.
 * anonymousId = user.anonymousId();
 * user.id('foo');
 * assert.equal(anonymousId, user.anonymousId());
 *
 * // didn't change because the user id changed to null.
 * anonymousId = user.anonymousId();
 * user.id('foo');
 * user.id(null);
 * assert.equal(anonymousId, user.anonymousId());
 *
 * // change because the user had previous id.
 * anonymousId = user.anonymousId();
 * user.id('foo');
 * user.id('baz'); // triggers change
 * user.id('baz'); // no change
 * assert.notEqual(anonymousId, user.anonymousId());
 */

User.prototype.id = function(id) {
  var prev = this._getId();
  var ret = Entity.prototype.id.apply(this, arguments);
  if (prev == null) return ret;
  // FIXME: We're relying on coercion here (1 == "1"), but our API treats these
  // two values differently. Figure out what will break if we remove this and
  // change to strict equality
  /* eslint-disable eqeqeq */
  if (prev != id && id) this.anonymousId(null);
  /* eslint-enable eqeqeq */
  return ret;
};

/**
 * Set / get / remove anonymousId.
 *
 * @param {String} anonymousId
 * @return {String|User}
 */

User.prototype.anonymousId = function(anonymousId) {
  var store = this.storage();

  // set / remove
  if (arguments.length) {
    store.set('ajs_anonymous_id', anonymousId);
    this._setAnonymousIdInLocalStorage(anonymousId);
    return this;
  }

  // new
  anonymousId = store.get('ajs_anonymous_id');
  if (anonymousId) {
    // value exists in cookie, copy it to localStorage
    this._setAnonymousIdInLocalStorage(anonymousId);
    // refresh cookie to extend expiry
    store.set('ajs_anonymous_id', anonymousId);
    return anonymousId;
  }

  if (!this._options.localStorageFallbackDisabled) {
    // if anonymousId doesn't exist in cookies, check localStorage
    anonymousId = localStorage.get('ajs_anonymous_id');
    if (anonymousId) {
      // Write to cookies if available in localStorage but not cookies
      store.set('ajs_anonymous_id', anonymousId);
      return anonymousId;
    }
  }

  // old - it is not stringified so we use the raw cookie.
  anonymousId = rawCookie('_sio');
  if (anonymousId) {
    anonymousId = anonymousId.split('----')[0];
    store.set('ajs_anonymous_id', anonymousId);
    this._setAnonymousIdInLocalStorage(anonymousId);
    store.remove('_sio');
    return anonymousId;
  }

  // empty
  anonymousId = uuid.v4();
  store.set('ajs_anonymous_id', anonymousId);
  this._setAnonymousIdInLocalStorage(anonymousId);
  return store.get('ajs_anonymous_id');
};

/**
 * Set the user's `anonymousid` in local storage.
 *
 * @param {String} id
 */

User.prototype._setAnonymousIdInLocalStorage = function(id) {
  if (!this._options.localStorageFallbackDisabled) {
    localStorage.set('ajs_anonymous_id', id);
  }
};

/**
 * Remove anonymous id on logout too.
 */

User.prototype.logout = function() {
  Entity.prototype.logout.call(this);
  this.anonymousId(null);
};

/**
 * Load saved user `id` or `traits` from storage.
 */

User.prototype.load = function() {
  if (this._loadOldCookie()) return;
  Entity.prototype.load.call(this);
};

/**
 * BACKWARDS COMPATIBILITY: Load the old user from the cookie.
 *
 * @api private
 * @return {boolean}
 */

User.prototype._loadOldCookie = function() {
  var user = cookie.get(this._options.cookie.oldKey);
  if (!user) return false;

  this.id(user.id);
  this.traits(user.traits);
  cookie.remove(this._options.cookie.oldKey);
  return true;
};

/**
 * Expose the user singleton.
 */

module.exports = bindAll(new User());

/**
 * Expose the `User` constructor.
 */

module.exports.User = User;

},{"./cookie":16,"./entity":17,"./store":25,"bind-all":44,"component-cookie":46,"debug":27,"inherits":64,"uuid":101}],27:[function(require,module,exports){

/**
 * Expose `debug()` as the module.
 */

module.exports = debug;

/**
 * Create a debugger with the given `name`.
 *
 * @param {String} name
 * @return {Type}
 * @api public
 */

function debug(name) {
  if (!debug.enabled(name)) return function(){};

  return function(fmt){
    fmt = coerce(fmt);

    var curr = new Date;
    var ms = curr - (debug[name] || curr);
    debug[name] = curr;

    fmt = name
      + ' '
      + fmt
      + ' +' + debug.humanize(ms);

    // This hackery is required for IE8
    // where `console.log` doesn't have 'apply'
    window.console
      && console.log
      && Function.prototype.apply.call(console.log, console, arguments);
  }
}

/**
 * The currently active debug mode names.
 */

debug.names = [];
debug.skips = [];

/**
 * Enables a debug mode by name. This can include modes
 * separated by a colon and wildcards.
 *
 * @param {String} name
 * @api public
 */

debug.enable = function(name) {
  try {
    localStorage.debug = name;
  } catch(e){}

  var split = (name || '').split(/[\s,]+/)
    , len = split.length;

  for (var i = 0; i < len; i++) {
    name = split[i].replace('*', '.*?');
    if (name[0] === '-') {
      debug.skips.push(new RegExp('^' + name.substr(1) + '$'));
    }
    else {
      debug.names.push(new RegExp('^' + name + '$'));
    }
  }
};

/**
 * Disable debug output.
 *
 * @api public
 */

debug.disable = function(){
  debug.enable('');
};

/**
 * Humanize the given `ms`.
 *
 * @param {Number} m
 * @return {String}
 * @api private
 */

debug.humanize = function(ms) {
  var sec = 1000
    , min = 60 * 1000
    , hour = 60 * min;

  if (ms >= hour) return (ms / hour).toFixed(1) + 'h';
  if (ms >= min) return (ms / min).toFixed(1) + 'm';
  if (ms >= sec) return (ms / sec | 0) + 's';
  return ms + 'ms';
};

/**
 * Returns true if the given mode name is enabled, false otherwise.
 *
 * @param {String} name
 * @return {Boolean}
 * @api public
 */

debug.enabled = function(name) {
  for (var i = 0, len = debug.skips.length; i < len; i++) {
    if (debug.skips[i].test(name)) {
      return false;
    }
  }
  for (var i = 0, len = debug.names.length; i < len; i++) {
    if (debug.names[i].test(name)) {
      return true;
    }
  }
  return false;
};

/**
 * Coerce `val`.
 */

function coerce(val) {
  if (val instanceof Error) return val.stack || val.message;
  return val;
}

// persist

try {
  if (window.localStorage) debug.enable(localStorage.debug);
} catch(e){}

},{}],28:[function(require,module,exports){
module.exports={
  "name": "@segment/analytics.js-core",
  "author": "Segment <friends@segment.com>",
  "version": "3.10.1",
  "description": "The hassle-free way to integrate analytics into any web application.",
  "keywords": [
    "analytics",
    "analytics.js",
    "segment",
    "segment.io"
  ],
  "main": "lib/index.js",
  "scripts": {
    "test": "make test",
    "lint": "eslint \"./{lib,test}/**/*.js\"",
    "format": "prettier-eslint --write --list-different \"./{lib,test}/**/*.{js,json,md}\"",
    "precommit": "lint-staged",
    "np": "np --no-publish",
    "cz": "git-cz",
    "commitmsg": "commitlint -E GIT_PARAMS"
  },
  "repository": {
    "type": "git",
    "url": "https://github.com/segmentio/analytics.js-core"
  },
  "license": "SEE LICENSE IN LICENSE",
  "bugs": {
    "url": "https://github.com/segmentio/analytics.js-core/issues"
  },
  "homepage": "https://github.com/segmentio/analytics.js-core#readme",
  "dependencies": {
    "@ndhoule/clone": "^1.0.0",
    "@ndhoule/defaults": "^2.0.1",
    "@ndhoule/each": "^2.0.1",
    "@ndhoule/extend": "^2.0.0",
    "@ndhoule/foldl": "^2.0.1",
    "@ndhoule/includes": "^2.0.1",
    "@ndhoule/keys": "^2.0.0",
    "@ndhoule/map": "^2.0.1",
    "@ndhoule/pick": "^2.0.0",
    "@segment/canonical": "^1.0.0",
    "@segment/is-meta": "^1.0.0",
    "@segment/isodate": "^1.0.2",
    "@segment/isodate-traverse": "^1.0.1",
    "@segment/prevent-default": "^1.0.0",
    "@segment/send-json": "^3.0.0",
    "@segment/store": "^1.3.20",
    "@segment/top-domain": "^3.0.0",
    "bind-all": "^1.0.0",
    "component-cookie": "^1.1.2",
    "component-emitter": "^1.2.1",
    "component-event": "^0.1.4",
    "component-querystring": "^2.0.0",
    "component-type": "^1.2.1",
    "component-url": "^0.2.1",
    "debug": "^0.7.4",
    "extend": "3.0.2",
    "inherits": "^2.0.1",
    "install": "^0.7.3",
    "is": "^3.1.0",
    "json3": "^3.3.2",
    "new-date": "^1.0.0",
    "next-tick": "^0.2.2",
    "segmentio-facade": "^3.0.2",
    "spark-md5": "^2.0.2",
    "uuid": "^2.0.2"
  },
  "devDependencies": {
    "@commitlint/cli": "^7.0.0",
    "@commitlint/config-conventional": "^7.0.1",
    "@segment/analytics.js-integration": "^3.2.1",
    "@segment/eslint-config": "^4.0.0",
    "browserify": "13.0.0",
    "browserify-istanbul": "^2.0.0",
    "codecov": "^3.0.2",
    "commitizen": "^2.10.1",
    "commitlint-circle": "^1.0.0",
    "compat-trigger-event": "^1.0.0",
    "component-each": "^0.2.6",
    "cz-conventional-changelog": "^2.1.0",
    "eslint": "^4.19.1",
    "eslint-config-prettier": "^2.9.0",
    "eslint-plugin-mocha": "^5.0.0",
    "eslint-plugin-react": "^7.9.1",
    "eslint-plugin-require-path-exists": "^1.1.8",
    "husky": "^0.14.3",
    "istanbul": "^0.4.3",
    "jquery": "^3.2.1",
    "karma": "1.3.0",
    "karma-browserify": "^5.0.4",
    "karma-chrome-launcher": "^1.0.1",
    "karma-coverage": "^1.0.0",
    "karma-junit-reporter": "^1.0.0",
    "karma-mocha": "1.0.1",
    "karma-phantomjs-launcher": "^1.0.0",
    "karma-sauce-launcher": "^1.0.0",
    "karma-spec-reporter": "0.0.26",
    "karma-summary-reporter": "^1.5.0",
    "lint-staged": "^7.2.0",
    "mocha": "^2.2.5",
    "np": "^3.0.4",
    "phantomjs-prebuilt": "^2.1.7",
    "prettier-eslint-cli": "^4.7.1",
    "proclaim": "^3.4.1",
    "sinon": "^1.7.3",
    "snyk": "^1.83.0",
    "watchify": "^3.7.0"
  },
  "commitlint": {
    "extends": [
      "@commitlint/config-conventional"
    ]
  },
  "lint-staged": {
    "linters": {
      "*.{js,json,md}": [
        "prettier-eslint --write",
        "git add"
      ]
    }
  },
  "config": {
    "commitizen": {
      "path": "cz-conventional-changelog"
    }
  }
}

},{}],29:[function(require,module,exports){
'use strict';

/**
 * Module dependencies.
 */

var bind = require('component-bind');
var cloneDeep = require('lodash.clonedeep');
var debug = require('debug');
var defaults = require('@ndhoule/defaults');
var extend = require('@ndhoule/extend');
var slug = require('slug-component');
var protos = require('./protos');
var statics = require('./statics');

/**
 * Create a new `Integration` constructor.
 *
 * @constructs Integration
 * @param {string} name
 * @return {Function} Integration
 */

function createIntegration(name) {
  /**
   * Initialize a new `Integration`.
   *
   * @class
   * @param {Object} options
   */

  function Integration(options) {
    if (options && options.addIntegration) {
      // plugin
      return options.addIntegration(Integration);
    }
    this.debug = debug('analytics:integration:' + slug(name));
    this.options = defaults(cloneDeep(options) || {}, this.defaults);
    this._queue = [];
    this.once('ready', bind(this, this.flush));

    Integration.emit('construct', this);
    this.ready = bind(this, this.ready);
    this._wrapInitialize();
    this._wrapPage();
    this._wrapTrack();
  }

  Integration.prototype.defaults = {};
  Integration.prototype.globals = [];
  Integration.prototype.templates = {};
  Integration.prototype.name = name;
  extend(Integration, statics);
  extend(Integration.prototype, protos);

  return Integration;
}

/**
 * Exports.
 */

module.exports = createIntegration;

},{"./protos":30,"./statics":31,"@ndhoule/defaults":4,"@ndhoule/extend":8,"component-bind":45,"debug":59,"lodash.clonedeep":70,"slug-component":92}],30:[function(require,module,exports){
'use strict';

/**
 * Module dependencies.
 */

var Emitter = require('component-emitter');
var after = require('@ndhoule/after');
var each = require('@ndhoule/each');
var events = require('analytics-events');
var every = require('@ndhoule/every');
var fmt = require('@segment/fmt');
var foldl = require('@ndhoule/foldl');
var is = require('is');
var loadIframe = require('load-iframe');
var loadScript = require('@segment/load-script');
var nextTick = require('next-tick');
var normalize = require('to-no-case');

/**
 * hasOwnProperty reference.
 */

var has = Object.prototype.hasOwnProperty;

/**
 * No operation.
 */

var noop = function noop() {};

/**
 * Window defaults.
 */

var onerror = window.onerror;
var onload = null;

/**
 * Mixin emitter.
 */

/* eslint-disable new-cap */
Emitter(exports);
/* eslint-enable new-cap */

/**
 * Initialize.
 */

exports.initialize = function() {
  var ready = this.ready;
  nextTick(ready);
};

/**
 * Loaded?
 *
 * @api private
 * @return {boolean}
 */

exports.loaded = function() {
  return false;
};

/**
 * Page.
 *
 * @api public
 * @param {Page} page
 */

/* eslint-disable no-unused-vars */
exports.page = function(page) {};
/* eslint-enable no-unused-vars */

/**
 * Track.
 *
 * @api public
 * @param {Track} track
 */

/* eslint-disable no-unused-vars */
exports.track = function(track) {};
/* eslint-enable no-unused-vars */

/**
 * Get values from items in `options` that are mapped to `key`.
 * `options` is an integration setting which is a collection
 * of type 'map', 'array', or 'mixed'
 *
 * Use cases include mapping events to pixelIds (map), sending generic
 * conversion pixels only for specific events (array), or configuring dynamic
 * mappings of event properties to query string parameters based on event (mixed)
 *
 * @api public
 * @param {Object|Object[]|String[]} options An object, array of objects, or
 * array of strings pulled from settings.mapping.
 * @param {string} key The name of the item in options whose metadata
 * we're looking for.
 * @return {Array} An array of settings that match the input `key` name.
 * @example
 *
 * // 'Map'
 * var events = { my_event: 'a4991b88' };
 * .map(events, 'My Event');
 * // => ["a4991b88"]
 * .map(events, 'whatever');
 * // => []
 *
 * // 'Array'
 * * var events = ['Completed Order', 'My Event'];
 * .map(events, 'My Event');
 * // => ["My Event"]
 * .map(events, 'whatever');
 * // => []
 *
 * // 'Mixed'
 * var events = [{ key: 'my event', value: '9b5eb1fa' }];
 * .map(events, 'my_event');
 * // => ["9b5eb1fa"]
 * .map(events, 'whatever');
 * // => []
 */

exports.map = function(options, key) {
  var normalizedComparator = normalize(key);
  var mappingType = getMappingType(options);

  if (mappingType === 'unknown') {
    return [];
  }

  return foldl(function(matchingValues, val, key) {
    var compare;
    var result;

    if (mappingType === 'map') {
      compare = key;
      result = val;
    }

    if (mappingType === 'array') {
      compare = val;
      result = val;
    }

    if (mappingType === 'mixed') {
      compare = val.key;
      result = val.value;
    }

    if (normalize(compare) === normalizedComparator) {
      matchingValues.push(result);
    }

    return matchingValues;
  }, [], options);
};

/**
 * Invoke a `method` that may or may not exist on the prototype with `args`,
 * queueing or not depending on whether the integration is "ready". Don't
 * trust the method call, since it contains integration party code.
 *
 * @api private
 * @param {string} method
 * @param {...*} args
 */

exports.invoke = function(method) {
  if (!this[method]) return;
  var args = Array.prototype.slice.call(arguments, 1);
  if (!this._ready) return this.queue(method, args);
  var ret;

  try {
    this.debug('%s with %o', method, args);
    ret = this[method].apply(this, args);
  } catch (e) {
    this.debug('error %o calling %s with %o', e, method, args);
  }

  return ret;
};

/**
 * Queue a `method` with `args`.
 *
 * @api private
 * @param {string} method
 * @param {Array} args
 */

exports.queue = function(method, args) {
  this._queue.push({ method: method, args: args });
};

/**
 * Flush the internal queue.
 *
 * @api private
 */

exports.flush = function() {
  this._ready = true;
  var self = this;

  each(function(call) {
    self[call.method].apply(self, call.args);
  }, this._queue);

  // Empty the queue.
  this._queue.length = 0;
};

/**
 * Reset the integration, removing its global variables.
 *
 * @api private
 */

exports.reset = function() {
  for (var i = 0; i < this.globals.length; i++) {
    window[this.globals[i]] = undefined;
  }

  window.onerror = onerror;
  window.onload = onload;
};

/**
 * Load a tag by `name`.
 *
 * @param {string} name The name of the tag.
 * @param {Object} locals Locals used to populate the tag's template variables
 * (e.g. `userId` in '<img src="https://whatever.com/{{ userId }}">').
 * @param {Function} [callback=noop] A callback, invoked when the tag finishes
 * loading.
 */

exports.load = function(name, locals, callback) {
  // Argument shuffling
  if (typeof name === 'function') { callback = name; locals = null; name = null; }
  if (name && typeof name === 'object') { callback = locals; locals = name; name = null; }
  if (typeof locals === 'function') { callback = locals; locals = null; }

  // Default arguments
  name = name || 'library';
  locals = locals || {};

  locals = this.locals(locals);
  var template = this.templates[name];
  if (!template) throw new Error(fmt('template "%s" not defined.', name));
  var attrs = render(template, locals);
  callback = callback || noop;
  var self = this;
  var el;

  switch (template.type) {
  case 'img':
    attrs.width = 1;
    attrs.height = 1;
    el = loadImage(attrs, callback);
    break;
  case 'script':
    el = loadScript(attrs, function(err) {
      if (!err) return callback();
      self.debug('error loading "%s" error="%s"', self.name, err);
    });
      // TODO: hack until refactoring load-script
    delete attrs.src;
    each(function(val, key) {
      el.setAttribute(key, val);
    }, attrs);
    break;
  case 'iframe':
    el = loadIframe(attrs, callback);
    break;
  default:
      // No default case
  }

  return el;
};

/**
 * Locals for tag templates.
 *
 * By default it includes a cache buster and all of the options.
 *
 * @param {Object} [locals]
 * @return {Object}
 */

exports.locals = function(locals) {
  locals = locals || {};
  var cache = Math.floor(new Date().getTime() / 3600000);
  if (!locals.hasOwnProperty('cache')) locals.cache = cache;
  each(function(val, key) {
    if (!locals.hasOwnProperty(key)) locals[key] = val;
  }, this.options);
  return locals;
};

/**
 * Simple way to emit ready.
 *
 * @api public
 */

exports.ready = function() {
  this.emit('ready');
};

/**
 * Wrap the initialize method in an exists check, so we don't have to do it for
 * every single integration.
 *
 * @api private
 */

exports._wrapInitialize = function() {
  var initialize = this.initialize;
  this.initialize = function() {
    this.debug('initialize');
    this._initialized = true;
    var ret = initialize.apply(this, arguments);
    this.emit('initialize');
    return ret;
  };
};

/**
 * Wrap the page method to call to noop the first page call if the integration assumes
 * a pageview.
 *
 * @api private
 */

exports._wrapPage = function() {
  // Noop the first page call if integration assumes pageview
  if (this._assumesPageview) return this.page = after(2, this.page);
};

/**
 * Wrap the track method to call other ecommerce methods if available depending
 * on the `track.event()`.
 *
 * @api private
 */

exports._wrapTrack = function() {
  var t = this.track;
  this.track = function(track) {
    var event = track.event();
    var called;
    var ret;

    for (var method in events) {
      if (has.call(events, method)) {
        var regexp = events[method];
        if (!this[method]) continue;
        if (!regexp.test(event)) continue;
        ret = this[method].apply(this, arguments);
        called = true;
        break;
      }
    }

    if (!called) ret = t.apply(this, arguments);
    return ret;
  };
};

/**
 * Determine the type of the option passed to `#map`
 *
 * @api private
 * @param {Object|Object[]} mapping
 * @return {String} mappingType
 */

function getMappingType(mapping) {
  if (is.array(mapping)) {
    return every(isMixed, mapping) ? 'mixed' : 'array';
  }
  if (is.object(mapping)) return 'map';
  return 'unknown';
}

/**
 * Determine if item in mapping array is a valid "mixed" type value
 *
 * Must be an object with properties "key" (of type string)
 * and "value" (of any type)
 *
 * @api private
 * @param {*} item
 * @return {Boolean}
 */

function isMixed(item) {
  if (!is.object(item)) return false;
  if (!is.string(item.key)) return false;
  if (!has.call(item, 'value')) return false;
  return true;
}

/**
 * TODO: Document me
 *
 * @api private
 * @param {Object} attrs
 * @param {Function} fn
 * @return {Image}
 */

function loadImage(attrs, fn) {
  fn = fn || function() {};
  var img = new Image();
  img.onerror = error(fn, 'failed to load pixel', img);
  img.onload = function() { fn(); };
  img.src = attrs.src;
  img.width = 1;
  img.height = 1;
  return img;
}

/**
 * TODO: Document me
 *
 * @api private
 * @param {Function} fn
 * @param {string} message
 * @param {Element} img
 * @return {Function}
 */

function error(fn, message, img) {
  return function(e) {
    e = e || window.event;
    var err = new Error(message);
    err.event = e;
    err.source = img;
    fn(err);
  };
}

/**
 * Render template + locals into an `attrs` object.
 *
 * @api private
 * @param {Object} template
 * @param {Object} locals
 * @return {Object}
 */

function render(template, locals) {
  return foldl(function(attrs, val, key) {
    attrs[key] = val.replace(/\{\{\ *(\w+)\ *\}\}/g, function(_, $1) {
      return locals[$1];
    });
    return attrs;
  }, {}, template.attrs);
}

},{"@ndhoule/after":1,"@ndhoule/each":6,"@ndhoule/every":7,"@ndhoule/foldl":9,"@segment/fmt":34,"@segment/load-script":38,"analytics-events":43,"component-emitter":52,"is":66,"load-iframe":69,"next-tick":76,"to-no-case":96}],31:[function(require,module,exports){
'use strict';

/**
 * Module dependencies.
 */

var Emitter = require('component-emitter');
var domify = require('domify');
var each = require('@ndhoule/each');
var includes = require('@ndhoule/includes');

/**
 * Mix in emitter.
 */

/* eslint-disable new-cap */
Emitter(exports);
/* eslint-enable new-cap */

/**
 * Add a new option to the integration by `key` with default `value`.
 *
 * @api public
 * @param {string} key
 * @param {*} value
 * @return {Integration}
 */

exports.option = function(key, value) {
  this.prototype.defaults[key] = value;
  return this;
};

/**
 * Add a new mapping option.
 *
 * This will create a method `name` that will return a mapping for you to use.
 *
 * @api public
 * @param {string} name
 * @return {Integration}
 * @example
 * Integration('My Integration')
 *   .mapping('events');
 *
 * new MyIntegration().track('My Event');
 *
 * .track = function(track){
 *   var events = this.events(track.event());
 *   each(send, events);
 *  };
 */

exports.mapping = function(name) {
  this.option(name, []);
  this.prototype[name] = function(key) {
    return this.map(this.options[name], key);
  };
  return this;
};

/**
 * Register a new global variable `key` owned by the integration, which will be
 * used to test whether the integration is already on the page.
 *
 * @api public
 * @param {string} key
 * @return {Integration}
 */

exports.global = function(key) {
  this.prototype.globals.push(key);
  return this;
};

/**
 * Mark the integration as assuming an initial pageview, so to defer the first page call, keep track of
 * whether we already nooped the first page call.
 *
 * @api public
 * @return {Integration}
 */

exports.assumesPageview = function() {
  this.prototype._assumesPageview = true;
  return this;
};

/**
 * Mark the integration as being "ready" once `load` is called.
 *
 * @api public
 * @return {Integration}
 */

exports.readyOnLoad = function() {
  this.prototype._readyOnLoad = true;
  return this;
};

/**
 * Mark the integration as being "ready" once `initialize` is called.
 *
 * @api public
 * @return {Integration}
 */

exports.readyOnInitialize = function() {
  this.prototype._readyOnInitialize = true;
  return this;
};

/**
 * Define a tag to be loaded.
 *
 * @api public
 * @param {string} [name='library'] A nicename for the tag, commonly used in
 * #load. Helpful when the integration has multiple tags and you need a way to
 * specify which of the tags you want to load at a given time.
 * @param {String} str DOM tag as string or URL.
 * @return {Integration}
 */

exports.tag = function(name, tag) {
  if (tag == null) {
    tag = name;
    name = 'library';
  }
  this.prototype.templates[name] = objectify(tag);
  return this;
};

/**
 * Given a string, give back DOM attributes.
 *
 * Do it in a way where the browser doesn't load images or iframes. It turns
 * out domify will load images/iframes because whenever you construct those
 * DOM elements, the browser immediately loads them.
 *
 * @api private
 * @param {string} str
 * @return {Object}
 */

function objectify(str) {
  // replace `src` with `data-src` to prevent image loading
  str = str.replace(' src="', ' data-src="');

  var el = domify(str);
  var attrs = {};

  each(function(attr) {
    // then replace it back
    var name = attr.name === 'data-src' ? 'src' : attr.name;
    if (!includes(attr.name + '=', str)) return;
    attrs[name] = attr.value;
  }, el.attributes);

  return {
    type: el.tagName.toLowerCase(),
    attrs: attrs
  };
}

},{"@ndhoule/each":6,"@ndhoule/includes":10,"component-emitter":52,"domify":61}],32:[function(require,module,exports){
var utf8Encode = require('utf8-encode');
var keyStr = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';

module.exports = encode;
function encode(input) {
    var output = "";
    var chr1, chr2, chr3, enc1, enc2, enc3, enc4;
    var i = 0;

    input = utf8Encode(input);

    while (i < input.length) {

        chr1 = input.charCodeAt(i++);
        chr2 = input.charCodeAt(i++);
        chr3 = input.charCodeAt(i++);

        enc1 = chr1 >> 2;
        enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
        enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
        enc4 = chr3 & 63;

        if (isNaN(chr2)) {
            enc3 = enc4 = 64;
        } else if (isNaN(chr3)) {
            enc4 = 64;
        }

        output = output +
            keyStr.charAt(enc1) + keyStr.charAt(enc2) +
            keyStr.charAt(enc3) + keyStr.charAt(enc4);

    }

    return output;
}
},{"utf8-encode":99}],33:[function(require,module,exports){
'use strict';

/**
 * Get the current page's canonical URL.
 *
 * @return {string|undefined}
 */
function canonical() {
  var tags = document.getElementsByTagName('link');
  // eslint-disable-next-line no-cond-assign
  for (var i = 0, tag; tag = tags[i]; i++) {
    if (tag.getAttribute('rel') === 'canonical') {
      return tag.getAttribute('href');
    }
  }
}

/*
 * Exports.
 */

module.exports = canonical;

},{}],34:[function(require,module,exports){
(function (global){(function (){
'use strict';

// Stringifier
var toString = global.JSON && typeof JSON.stringify === 'function' ? JSON.stringify : String;

/**
 * Format the given `str`.
 *
 * @param {string} str
 * @param {...*} [args]
 * @return {string}
 */
function fmt(str) {
  var args = Array.prototype.slice.call(arguments, 1);
  var j = 0;

  return str.replace(/%([a-z])/gi, function(match, f) {
    return fmt[f] ? fmt[f](args[j++]) : match + f;
  });
}

// Formatters
fmt.o = toString;
fmt.s = String;
fmt.d = parseInt;

/*
 * Exports.
 */

module.exports = fmt;

}).call(this)}).call(this,typeof global !== "undefined" ? global : typeof self !== "undefined" ? self : typeof window !== "undefined" ? window : {})
},{}],35:[function(require,module,exports){
'use strict';

function isMeta(e) {
  if (e.metaKey || e.altKey || e.ctrlKey || e.shiftKey) {
    return true;
  }

  // Logic that handles checks for the middle mouse button, based
  // on [jQuery](https://github.com/jquery/jquery/blob/master/src/event.js#L466).
  var which = e.which;
  var button = e.button;
  if (!which && button !== undefined) {
    // eslint-disable-next-line no-bitwise, no-extra-parens
    return (!button & 1) && (!button & 2) && (button & 4);
  } else if (which === 2) {
    return true;
  }

  return false;
}

/*
 * Exports.
 */

module.exports = isMeta;

},{}],36:[function(require,module,exports){
'use strict';

var type = require('component-type');
var each = require('component-each');
var isodate = require('@segment/isodate');

/**
 * Expose `traverse`.
 */

module.exports = traverse;

/**
 * Traverse an object or array, and return a clone with all ISO strings parsed
 * into Date objects.
 *
 * @param {Object} obj
 * @return {Object}
 */

function traverse(input, strict) {
  if (strict === undefined) strict = true;

  if (type(input) === 'object') return object(input, strict);
  if (type(input) === 'array') return array(input, strict);
  return input;
}

/**
 * Object traverser.
 *
 * @param {Object} obj
 * @param {Boolean} strict
 * @return {Object}
 */

function object(obj, strict) {
  // 'each' utility uses obj.length to check whether the obj is array. To avoid incorrect classification, wrap call to 'each' with rename of obj.length
  if (obj.length && typeof obj.length === 'number' && !(obj.length - 1 in obj)) { // cross browser compatible way of checking has length and is not array
    obj.lengthNonArray = obj.length;
    delete obj.length;
  }
  each(obj, function(key, val) {
    if (isodate.is(val, strict)) {
      obj[key] = isodate.parse(val);
    } else if (type(val) === 'object' || type(val) === 'array') {
      traverse(val, strict);
    }
  });
  // restore obj.length if it was renamed
  if (obj.lengthNonArray) {
    obj.length = obj.lengthNonArray;
    delete obj.lengthNonArray;
  }
  return obj;
}

/**
 * Array traverser.
 *
 * @param {Array} arr
 * @param {Boolean} strict
 * @return {Array}
 */

function array(arr, strict) {
  each(arr, function(val, x) {
    if (type(val) === 'object') {
      traverse(val, strict);
    } else if (isodate.is(val, strict)) {
      arr[x] = isodate.parse(val);
    }
  });
  return arr;
}

},{"@segment/isodate":37,"component-each":50,"component-type":57}],37:[function(require,module,exports){
'use strict';

/**
 * Matcher, slightly modified from:
 *
 * https://github.com/csnover/js-iso8601/blob/lax/iso8601.js
 */

var matcher = /^(\d{4})(?:-?(\d{2})(?:-?(\d{2}))?)?(?:([ T])(\d{2}):?(\d{2})(?::?(\d{2})(?:[,\.](\d{1,}))?)?(?:(Z)|([+\-])(\d{2})(?::?(\d{2}))?)?)?$/;

/**
 * Convert an ISO date string to a date. Fallback to native `Date.parse`.
 *
 * https://github.com/csnover/js-iso8601/blob/lax/iso8601.js
 *
 * @param {String} iso
 * @return {Date}
 */

exports.parse = function(iso) {
  var numericKeys = [1, 5, 6, 7, 11, 12];
  var arr = matcher.exec(iso);
  var offset = 0;

  // fallback to native parsing
  if (!arr) {
    return new Date(iso);
  }

  /* eslint-disable no-cond-assign */
  // remove undefined values
  for (var i = 0, val; val = numericKeys[i]; i++) {
    arr[val] = parseInt(arr[val], 10) || 0;
  }
  /* eslint-enable no-cond-assign */

  // allow undefined days and months
  arr[2] = parseInt(arr[2], 10) || 1;
  arr[3] = parseInt(arr[3], 10) || 1;

  // month is 0-11
  arr[2]--;

  // allow abitrary sub-second precision
  arr[8] = arr[8] ? (arr[8] + '00').substring(0, 3) : 0;

  // apply timezone if one exists
  if (arr[4] === ' ') {
    offset = new Date().getTimezoneOffset();
  } else if (arr[9] !== 'Z' && arr[10]) {
    offset = arr[11] * 60 + arr[12];
    if (arr[10] === '+') {
      offset = 0 - offset;
    }
  }

  var millis = Date.UTC(arr[1], arr[2], arr[3], arr[5], arr[6] + offset, arr[7], arr[8]);
  return new Date(millis);
};


/**
 * Checks whether a `string` is an ISO date string. `strict` mode requires that
 * the date string at least have a year, month and date.
 *
 * @param {String} string
 * @param {Boolean} strict
 * @return {Boolean}
 */

exports.is = function(string, strict) {
  if (typeof string !== 'string') {
    return false;
  }
  if (strict && (/^\d{4}-\d{2}-\d{2}/).test(string) === false) {
    return false;
  }
  return matcher.test(string);
};

},{}],38:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var onload = require('script-onload');
var tick = require('next-tick');
var type = require('component-type');

/**
 * Loads a script asynchronously.
 *
 * @param {Object} options
 * @param {Function} cb
 */
function loadScript(options, cb) {
  if (!options) {
    throw new Error('Can\'t load nothing...');
  }

  // Allow for the simplest case, just passing a `src` string.
  if (type(options) === 'string') {
    options = { src : options };
  }

  var https = document.location.protocol === 'https:' || document.location.protocol === 'chrome-extension:';

  // If you use protocol relative URLs, third-party scripts like Google
  // Analytics break when testing with `file:` so this fixes that.
  if (options.src && options.src.indexOf('//') === 0) {
    options.src = (https ? 'https:' : 'http:') + options.src;
  }

  // Allow them to pass in different URLs depending on the protocol.
  if (https && options.https) {
    options.src = options.https;
  } else if (!https && options.http) {
    options.src = options.http;
  }

  // Make the `<script>` element and insert it before the first script on the
  // page, which is guaranteed to exist since this Javascript is running.
  var script = document.createElement('script');
  script.type = 'text/javascript';
  script.async = true;
  script.src = options.src;

  // If we have a cb, attach event handlers. Does not work on < IE9 because
  // older browser versions don't register element.onerror
  if (type(cb) === 'function') {
    onload(script, cb);
  }

  tick(function() {
    // Append after event listeners are attached for IE.
    var firstScript = document.getElementsByTagName('script')[0];
    firstScript.parentNode.insertBefore(script, firstScript);
  });

  // Return the script element in case they want to do anything special, like
  // give it an ID or attributes.
  return script;
}

/*
 * Exports.
 */

module.exports = loadScript;

},{"component-type":57,"next-tick":76,"script-onload":79}],39:[function(require,module,exports){
'use strict';

/**
 * Prevent default on a given event.
 *
 * @param {Event} e
 * @example
 * anchor.onclick = prevent;
 * anchor.onclick = function(e){
 *   if (something) return prevent(e);
 * };
 */

function preventDefault(e) {
  e = e || window.event;
  return e.preventDefault ? e.preventDefault() : e.returnValue = false;
}

/*
 * Exports.
 */

module.exports = preventDefault;

},{}],40:[function(require,module,exports){
'use strict';

/*
 * Module dependencies.
 */

var JSON = require('json3');
var base64encode = require('@segment/base64-encode');
var cors = require('has-cors');
var jsonp = require('jsonp');

/*
 * Exports.
 */

exports = module.exports = cors ? json : base64;

/**
 * Expose `callback`
 */

exports.callback = 'callback';

/**
 * Expose `prefix`
 */

exports.prefix = 'data';

/**
 * Expose `json`.
 */

exports.json = json;

/**
 * Expose `base64`.
 */

exports.base64 = base64;

/**
 * Expose `type`
 */

exports.type = cors ? 'xhr' : 'jsonp';

/**
 * Send the given `obj` to `url` with `fn(err, req)`.
 *
 * @param {String} url
 * @param {Object} obj
 * @param {Object} headers
 * @param {Function} fn
 * @api private
 */

function json(url, obj, headers, fn) {
  if (arguments.length === 3) fn = headers, headers = {};

  var req = new XMLHttpRequest;
  req.onerror = fn;
  req.onreadystatechange = done;
  req.open('POST', url, true);

  // TODO: Remove this eslint disable
  // eslint-disable-next-line guard-for-in
  for (var k in headers) {
    req.setRequestHeader(k, headers[k]);
  }
  req.send(JSON.stringify(obj));

  function done() {
    if (req.readyState === 4) {
      return fn(null, req);
    }
  }
}

/**
 * Send the given `obj` to `url` with `fn(err, req)`.
 *
 * @param {String} url
 * @param {Object} obj
 * @param {Function} fn
 * @api private
 */

function base64(url, obj, _, fn) {
  if (arguments.length === 3) fn = _;

  var prefix = exports.prefix;
  var data = encode(obj);
  url += '?' + prefix + '=' + data;
  jsonp(url, { param: exports.callback }, function(err, obj) {
    if (err) return fn(err);
    fn(null, {
      url: url,
      body: obj
    });
  });
}

/**
 * Encodes `obj`.
 *
 * @param {Object} obj
 */

function encode(obj) {
  var str = '';
  str = JSON.stringify(obj);
  str = base64encode(str);
  str = str.replace(/\+/g, '-').replace(/\//g, '_');
  return encodeURIComponent(str);
}

},{"@segment/base64-encode":32,"has-cors":63,"json3":67,"jsonp":68}],41:[function(require,module,exports){
(function (global){(function (){
"use strict"

var JSON = require('json3');

module.exports = (function() {
	// Store.js
	var store = {},
		win = (typeof window != 'undefined' ? window : global),
		doc = win.document,
		localStorageName = 'localStorage',
		scriptTag = 'script',
		storage

	store.disabled = false
	store.version = '1.3.20'
	store.set = function(key, value) {}
	store.get = function(key, defaultVal) {}
	store.has = function(key) { return store.get(key) !== undefined }
	store.remove = function(key) {}
	store.clear = function() {}
	store.transact = function(key, defaultVal, transactionFn) {
		if (transactionFn == null) {
			transactionFn = defaultVal
			defaultVal = null
		}
		if (defaultVal == null) {
			defaultVal = {}
		}
		var val = store.get(key, defaultVal)
		transactionFn(val)
		store.set(key, val)
	}
	store.getAll = function() {
		var ret = {}
		store.forEach(function(key, val) {
			ret[key] = val
		})
		return ret
	}
	store.forEach = function() {}
	store.serialize = function(value) {
		return JSON.stringify(value)
	}
	store.deserialize = function(value) {
		if (typeof value != 'string') { return undefined }
		try { return JSON.parse(value) }
		catch(e) { return value || undefined }
	}

	// Functions to encapsulate questionable FireFox 3.6.13 behavior
	// when about.config::dom.storage.enabled === false
	// See https://github.com/marcuswestin/store.js/issues#issue/13
	function isLocalStorageNameSupported() {
		try { return (localStorageName in win && win[localStorageName]) }
		catch(err) { return false }
	}

	if (isLocalStorageNameSupported()) {
		storage = win[localStorageName]
		store.set = function(key, val) {
			if (val === undefined) { return store.remove(key) }
			storage.setItem(key, store.serialize(val))
			return val
		}
		store.get = function(key, defaultVal) {
			var val = store.deserialize(storage.getItem(key))
			return (val === undefined ? defaultVal : val)
		}
		store.remove = function(key) { storage.removeItem(key) }
		store.clear = function() { storage.clear() }
		store.forEach = function(callback) {
			for (var i=0; i<storage.length; i++) {
				var key = storage.key(i)
				callback(key, store.get(key))
			}
		}
	} else if (doc && doc.documentElement.addBehavior) {
		var storageOwner,
			storageContainer
		// Since #userData storage applies only to specific paths, we need to
		// somehow link our data to a specific path.  We choose /favicon.ico
		// as a pretty safe option, since all browsers already make a request to
		// this URL anyway and being a 404 will not hurt us here.  We wrap an
		// iframe pointing to the favicon in an ActiveXObject(htmlfile) object
		// (see: http://msdn.microsoft.com/en-us/library/aa752574(v=VS.85).aspx)
		// since the iframe access rules appear to allow direct access and
		// manipulation of the document element, even for a 404 page.  This
		// document can be used instead of the current document (which would
		// have been limited to the current path) to perform #userData storage.
		try {
			storageContainer = new ActiveXObject('htmlfile')
			storageContainer.open()
			storageContainer.write('<'+scriptTag+'>document.w=window</'+scriptTag+'><iframe src="/favicon.ico"></iframe>')
			storageContainer.close()
			storageOwner = storageContainer.w.frames[0].document
			storage = storageOwner.createElement('div')
		} catch(e) {
			// somehow ActiveXObject instantiation failed (perhaps some special
			// security settings or otherwse), fall back to per-path storage
			storage = doc.createElement('div')
			storageOwner = doc.body
		}
		var withIEStorage = function(storeFunction) {
			return function() {
				var args = Array.prototype.slice.call(arguments, 0)
				args.unshift(storage)
				// See http://msdn.microsoft.com/en-us/library/ms531081(v=VS.85).aspx
				// and http://msdn.microsoft.com/en-us/library/ms531424(v=VS.85).aspx
				storageOwner.appendChild(storage)
				storage.addBehavior('#default#userData')
				storage.load(localStorageName)
				var result = storeFunction.apply(store, args)
				storageOwner.removeChild(storage)
				return result
			}
		}

		// In IE7, keys cannot start with a digit or contain certain chars.
		// See https://github.com/marcuswestin/store.js/issues/40
		// See https://github.com/marcuswestin/store.js/issues/83
		var forbiddenCharsRegex = new RegExp("[!\"#$%&'()*+,/\\\\:;<=>?@[\\]^`{|}~]", "g")
		var ieKeyFix = function(key) {
			return key.replace(/^d/, '___$&').replace(forbiddenCharsRegex, '___')
		}
		store.set = withIEStorage(function(storage, key, val) {
			key = ieKeyFix(key)
			if (val === undefined) { return store.remove(key) }
			storage.setAttribute(key, store.serialize(val))
			storage.save(localStorageName)
			return val
		})
		store.get = withIEStorage(function(storage, key, defaultVal) {
			key = ieKeyFix(key)
			var val = store.deserialize(storage.getAttribute(key))
			return (val === undefined ? defaultVal : val)
		})
		store.remove = withIEStorage(function(storage, key) {
			key = ieKeyFix(key)
			storage.removeAttribute(key)
			storage.save(localStorageName)
		})
		store.clear = withIEStorage(function(storage) {
			var attributes = storage.XMLDocument.documentElement.attributes
			storage.load(localStorageName)
			for (var i=attributes.length-1; i>=0; i--) {
				storage.removeAttribute(attributes[i].name)
			}
			storage.save(localStorageName)
		})
		store.forEach = withIEStorage(function(storage, callback) {
			var attributes = storage.XMLDocument.documentElement.attributes
			for (var i=0, attr; attr=attributes[i]; ++i) {
				callback(attr.name, store.deserialize(storage.getAttribute(attr.name)))
			}
		})
	}

	try {
		var testKey = '__storejs__'
		store.set(testKey, testKey)
		if (store.get(testKey) != testKey) { store.disabled = true }
		store.remove(testKey)
	} catch(e) {
		store.disabled = true
	}
	store.enabled = !store.disabled
	
	return store
}())

}).call(this)}).call(this,typeof global !== "undefined" ? global : typeof self !== "undefined" ? self : typeof window !== "undefined" ? window : {})
},{"json3":67}],42:[function(require,module,exports){
'use strict';

/**
 * Module dependencies.
 */

var parse = require('component-url').parse;
var cookie = require('component-cookie');

/**
 * Get the top domain.
 *
 * The function constructs the levels of domain and attempts to set a global
 * cookie on each one when it succeeds it returns the top level domain.
 *
 * The method returns an empty string when the hostname is an ip or `localhost`.
 *
 * Example levels:
 *
 *      domain.levels('http://www.google.co.uk');
 *      // => ["co.uk", "google.co.uk", "www.google.co.uk"]
 *
 * Example:
 *
 *      domain('http://localhost:3000/baz');
 *      // => ''
 *      domain('http://dev:3000/baz');
 *      // => ''
 *      domain('http://127.0.0.1:3000/baz');
 *      // => ''
 *      domain('http://segment.io/baz');
 *      // => 'segment.io'
 *
 * @param {string} url
 * @return {string}
 * @api public
 */
function domain(url) {
  var cookie = exports.cookie;
  var levels = exports.levels(url);

  // Lookup the real top level one.
  for (var i = 0; i < levels.length; ++i) {
    var cname = '__tld__';
    var domain = levels[i];
    var opts = { domain: '.' + domain };

    cookie(cname, 1, opts);
    if (cookie(cname)) {
      cookie(cname, null, opts);
      return domain;
    }
  }

  return '';
}

/**
 * Levels returns all levels of the given url.
 *
 * @param {string} url
 * @return {Array}
 * @api public
 */
domain.levels = function(url) {
  var host = parse(url).hostname;
  var parts = host.split('.');
  var last = parts[parts.length - 1];
  var levels = [];

  // Ip address.
  if (parts.length === 4 && last === parseInt(last, 10)) {
    return levels;
  }

  // Localhost.
  if (parts.length <= 1) {
    return levels;
  }

  // Create levels.
  for (var i = parts.length - 2; i >= 0; --i) {
    levels.push(parts.slice(i).join('.'));
  }

  return levels;
};

/**
 * Expose cookie on domain.
 */
domain.cookie = cookie;

/*
 * Exports.
 */

exports = module.exports = domain;

},{"component-cookie":46,"component-url":58}],43:[function(require,module,exports){
'use strict';

/**
 * Module Dependencies
 */

var map = require('@ndhoule/map');
var foldl = require('@ndhoule/foldl');

var eventMap = {
  // Videos
  videoPlaybackStarted: [{
    object: 'video playback',
    action: 'started'
  }],
  videoPlaybackPaused: [{
    object: 'video playback',
    action: 'paused'
  }],
  videoPlaybackInterrupted: [{
    object: 'video playback',
    action: 'interrupted'
  }],
  videoPlaybackResumed: [{
    object: 'video playback',
    action: 'resumed'
  }],
  videoPlaybackCompleted: [{
    object: 'video playback',
    action: 'completed'
  }],
  videoPlaybackBufferStarted: [{
    object: 'video playback buffer',
    action: 'started'
  }],
  videoPlaybackBufferCompleted: [{
    object: 'video playback buffer',
    action: 'completed'
  }],
  videoPlaybackSeekStarted: [{
    object: 'video playback seek',
    action: 'started'
  }],
  videoPlaybackSeekCompleted: [{
    object: 'video playback seek',
    action: 'completed'
  }],
  videoContentStarted: [{
    object: 'video content',
    action: 'started'
  }],
  videoContentPlaying: [{
    object: 'video content',
    action: 'playing'
  }],
  videoContentCompleted: [{
    object: 'video content',
    action: 'completed'
  }],
  videoAdStarted: [{
    object: 'video ad',
    action: 'started'
  }],
  videoAdPlaying: [{
    object: 'video ad',
    action: 'playing'
  }],
  videoAdCompleted: [{
    object: 'video ad',
    action: 'completed'
  }],

  // Promotions
  promotionViewed: [{
    object: 'promotion',
    action: 'viewed'
  }],
  promotionClicked: [{
    object: 'promotion',
    action: 'clicked'
  }],

  // Browsing
  productsSearched: [{
    object: 'products',
    action: 'searched'
  }],
  productListViewed: [{
    object: 'product list',
    action: 'viewed'
  }, {
    object: 'product category',
    action: 'viewed'
  }],
  productListFiltered: [{
    object: 'product list',
    action: 'filtered'
  }],

  // Core Ordering
  productClicked: [{
    object: 'product',
    action: 'clicked'
  }],
  productViewed: [{
    object: 'product',
    action: 'viewed'
  }],
  productAdded: [{
    object: 'product',
    action: 'added'
  }],
  productRemoved: [{
    object: 'product',
    action: 'removed'
  }],
  cartViewed: [{
    object: 'cart',
    action: 'viewed'
  }],
  orderUpdated: [{
    object: 'order',
    action: 'updated'
  }],
  orderCompleted: [{
    object: 'order',
    action: 'completed'
  }],
  orderRefunded: [{
    object: 'order',
    action: 'refunded'
  }],
  orderCancelled: [{
    object: 'order',
    action: 'cancelled'
  }],
  paymentInfoEntered: [{
    object: 'payment info',
    action: 'entered'
  }],
  checkoutStarted: [{
    object: 'checkout',
    action: 'started'
  }],
  checkoutStepViewed: [{
    object: 'checkout step',
    action: 'viewed'
  }],
  checkoutStepCompleted: [{
    object: 'checkout step',
    action: 'completed'
  }],

  // Coupons
  couponEntered: [{
    object: 'coupon',
    action: 'entered'
  }],
  couponApplied: [{
    object: 'coupon',
    action: 'applied'
  }],
  couponDenied: [{
    object: 'coupon',
    action: 'denied'
  }],
  couponRemoved: [{
    object: 'coupon',
    action: 'removed'
  }],

  // Wishlisting
  productAddedToWishlist: [{
    object: 'product',
    action: 'added to wishlist'
  }],
  productRemovedFromWishlist: [{
    object: 'product',
    action: 'removed from wishlist'
  }],
  productAddedFromWishlistToCart: [{
    object: 'product',
    action: 'added to cart from wishlist'
  }, {
    object: 'product',
    action: 'added from wishlist to cart'
  }],

  // Sharing
  productShared: [{
    object: 'product',
    action: 'shared'
  }],
  cartShared: [{
    object: 'cart',
    action: 'shared'
  }],

  // Reviewing
  productReviewed: [{
    object: 'product',
    action: 'reviewed'
  }],

  // App Lifecycle
  applicationInstalled: [{
    object: 'application',
    action: 'installed'
  }],
  applicationUpdated: [{
    object: 'application',
    action: 'updated'
  }],
  applicationOpened: [{
    object: 'application',
    action: 'opened'
  }],
  applicationBackgrounded: [{
    object: 'application',
    action: 'backgrounded'
  }],
  applicationUninstalled: [{
    object: 'application',
    action: 'uninstalled'
  }],
  applicationCrashed: [{
    object: 'application',
    action: 'crashed'
  }],

  // App Campaign and Referral Events
  installAttributed: [{
    object: 'install',
    action: 'attributed'
  }],
  deepLinkOpened: [{
    object: 'deep link',
    action: 'opened'
  }],
  pushNotificationReceived: [{
    object: 'push notification',
    action: 'received'
  }],
  pushNotificationTapped: [{
    object: 'push notification',
    action: 'tapped'
  }],
  pushNotificationBounced: [{
    object: 'push notification',
    action: 'bounced'
  }],

  // Email
  emailBounced: [{
    object: 'email',
    action: 'bounced'
  }],
  emailDelivered: [{
    object: 'email',
    action: 'delivered'
  }],
  emailLinkClicked: [{
    object: 'email link',
    action: 'clicked'
  }],
  emailMarkedAsSpam: [{
    object: 'email',
    action: 'marked as spam'
  }],
  emailOpened: [{
    object: 'email',
    action: 'opened'
  }],
  unsubscribed: [{
    object: '',
    action: 'unsubscribed'
  }]
};

/**
 * Export the event map
 *
 * For each method
 *   - For each of its object:action alias pairs
 *     - For each permutation of that pair
 *       - Create a regex string
 *   - Join them and assign it to its respective method value
 *
 *  [{
 *    object: 'product list',
 *    action: 'viewed'
 *  },{
 *    object: 'product category',
 *    action: 'viewed'
 *  }] => /
 *    ^[ _]?product[ _]?list[ _]?viewed[ _]?
 *   |^[ _]?viewed[ _]?product[ _]?list[ _]?
 *   |^[ _]?product[ _]?category[ _]?viewed[ _]?
 *   |^[ _]?viewed[ _]?product[ _]?category[ _]?
 *   $/i
 *
 *  todo(cs/wj/nh): memoization strategy / build step?
 */

module.exports = foldl(function transform(ret, pairs, method) {
  var values = map(function(pair) {
    return map(function(permutation) {
      var flattened = [].concat.apply([], map(function(words) {
        return words.split(' ');
      }, permutation));
      return '^[ _]?' + flattened.join('[ _]?') + '[ _]?';
    }, [
      [pair.action, pair.object],
      [pair.object, pair.action]
    ]).join('|');
  }, pairs);
  var conjoined = values.join('|') + '$';
  ret[method] = new RegExp(conjoined, 'i');
  return ret;
}, {}, eventMap);

},{"@ndhoule/foldl":9,"@ndhoule/map":12}],44:[function(require,module,exports){
'use strict';

var bind = require('component-bind');

function bindAll(obj) {
  // eslint-disable-next-line guard-for-in
  for (var key in obj) {
    var val = obj[key];
    if (typeof val === 'function') {
      obj[key] = bind(obj, obj[key]);
    }
  }
  return obj;
}

module.exports = bindAll;

},{"component-bind":45}],45:[function(require,module,exports){
/**
 * Slice reference.
 */

var slice = [].slice;

/**
 * Bind `obj` to `fn`.
 *
 * @param {Object} obj
 * @param {Function|String} fn or string
 * @return {Function}
 * @api public
 */

module.exports = function(obj, fn){
  if ('string' == typeof fn) fn = obj[fn];
  if ('function' != typeof fn) throw new Error('bind() requires a function');
  var args = slice.call(arguments, 2);
  return function(){
    return fn.apply(obj, args.concat(slice.call(arguments)));
  }
};

},{}],46:[function(require,module,exports){

/**
 * Module dependencies.
 */

var debug = require('debug')('cookie');

/**
 * Set or get cookie `name` with `value` and `options` object.
 *
 * @param {String} name
 * @param {String} value
 * @param {Object} options
 * @return {Mixed}
 * @api public
 */

module.exports = function(name, value, options){
  switch (arguments.length) {
    case 3:
    case 2:
      return set(name, value, options);
    case 1:
      return get(name);
    default:
      return all();
  }
};

/**
 * Set cookie `name` to `value`.
 *
 * @param {String} name
 * @param {String} value
 * @param {Object} options
 * @api private
 */

function set(name, value, options) {
  options = options || {};
  var str = encode(name) + '=' + encode(value);

  if (null == value) options.maxage = -1;

  if (options.maxage) {
    options.expires = new Date(+new Date + options.maxage);
  }

  if (options.path) str += '; path=' + options.path;
  if (options.domain) str += '; domain=' + options.domain;
  if (options.expires) str += '; expires=' + options.expires.toUTCString();
  if (options.secure) str += '; secure';

  document.cookie = str;
}

/**
 * Return all cookies.
 *
 * @return {Object}
 * @api private
 */

function all() {
  var str;
  try {
    str = document.cookie;
  } catch (err) {
    if (typeof console !== 'undefined' && typeof console.error === 'function') {
      console.error(err.stack || err);
    }
    return {};
  }
  return parse(str);
}

/**
 * Get cookie `name`.
 *
 * @param {String} name
 * @return {String}
 * @api private
 */

function get(name) {
  return all()[name];
}

/**
 * Parse cookie `str`.
 *
 * @param {String} str
 * @return {Object}
 * @api private
 */

function parse(str) {
  var obj = {};
  var pairs = str.split(/ *; */);
  var pair;
  if ('' == pairs[0]) return obj;
  for (var i = 0; i < pairs.length; ++i) {
    pair = pairs[i].split('=');
    obj[decode(pair[0])] = decode(pair[1]);
  }
  return obj;
}

/**
 * Encode.
 */

function encode(value){
  try {
    return encodeURIComponent(value);
  } catch (e) {
    debug('error `encode(%o)` - %o', value, e)
  }
}

/**
 * Decode.
 */

function decode(value) {
  try {
    return decodeURIComponent(value);
  } catch (e) {
    debug('error `decode(%o)` - %o', value, e)
  }
}

},{"debug":47}],47:[function(require,module,exports){

/**
 * This is the web browser implementation of `debug()`.
 *
 * Expose `debug()` as the module.
 */

exports = module.exports = require('./debug');
exports.log = log;
exports.formatArgs = formatArgs;
exports.save = save;
exports.load = load;
exports.useColors = useColors;
exports.storage = 'undefined' != typeof chrome
               && 'undefined' != typeof chrome.storage
                  ? chrome.storage.local
                  : localstorage();

/**
 * Colors.
 */

exports.colors = [
  'lightseagreen',
  'forestgreen',
  'goldenrod',
  'dodgerblue',
  'darkorchid',
  'crimson'
];

/**
 * Currently only WebKit-based Web Inspectors, Firefox >= v31,
 * and the Firebug extension (any Firefox version) are known
 * to support "%c" CSS customizations.
 *
 * TODO: add a `localStorage` variable to explicitly enable/disable colors
 */

function useColors() {
  // is webkit? http://stackoverflow.com/a/16459606/376773
  return ('WebkitAppearance' in document.documentElement.style) ||
    // is firebug? http://stackoverflow.com/a/398120/376773
    (window.console && (console.firebug || (console.exception && console.table))) ||
    // is firefox >= v31?
    // https://developer.mozilla.org/en-US/docs/Tools/Web_Console#Styling_messages
    (navigator.userAgent.toLowerCase().match(/firefox\/(\d+)/) && parseInt(RegExp.$1, 10) >= 31);
}

/**
 * Map %j to `JSON.stringify()`, since no Web Inspectors do that by default.
 */

exports.formatters.j = function(v) {
  return JSON.stringify(v);
};


/**
 * Colorize log arguments if enabled.
 *
 * @api public
 */

function formatArgs() {
  var args = arguments;
  var useColors = this.useColors;

  args[0] = (useColors ? '%c' : '')
    + this.namespace
    + (useColors ? ' %c' : ' ')
    + args[0]
    + (useColors ? '%c ' : ' ')
    + '+' + exports.humanize(this.diff);

  if (!useColors) return args;

  var c = 'color: ' + this.color;
  args = [args[0], c, 'color: inherit'].concat(Array.prototype.slice.call(args, 1));

  // the final "%c" is somewhat tricky, because there could be other
  // arguments passed either before or after the %c, so we need to
  // figure out the correct index to insert the CSS into
  var index = 0;
  var lastC = 0;
  args[0].replace(/%[a-z%]/g, function(match) {
    if ('%%' === match) return;
    index++;
    if ('%c' === match) {
      // we only are interested in the *last* %c
      // (the user may have provided their own)
      lastC = index;
    }
  });

  args.splice(lastC, 0, c);
  return args;
}

/**
 * Invokes `console.log()` when available.
 * No-op when `console.log` is not a "function".
 *
 * @api public
 */

function log() {
  // this hackery is required for IE8/9, where
  // the `console.log` function doesn't have 'apply'
  return 'object' === typeof console
    && console.log
    && Function.prototype.apply.call(console.log, console, arguments);
}

/**
 * Save `namespaces`.
 *
 * @param {String} namespaces
 * @api private
 */

function save(namespaces) {
  try {
    if (null == namespaces) {
      exports.storage.removeItem('debug');
    } else {
      exports.storage.debug = namespaces;
    }
  } catch(e) {}
}

/**
 * Load `namespaces`.
 *
 * @return {String} returns the previously persisted debug modes
 * @api private
 */

function load() {
  var r;
  try {
    r = exports.storage.debug;
  } catch(e) {}
  return r;
}

/**
 * Enable namespaces listed in `localStorage.debug` initially.
 */

exports.enable(load());

/**
 * Localstorage attempts to return the localstorage.
 *
 * This is necessary because safari throws
 * when a user disables cookies/localstorage
 * and you attempt to access it.
 *
 * @return {LocalStorage}
 * @api private
 */

function localstorage(){
  try {
    return window.localStorage;
  } catch (e) {}
}

},{"./debug":48}],48:[function(require,module,exports){

/**
 * This is the common logic for both the Node.js and web browser
 * implementations of `debug()`.
 *
 * Expose `debug()` as the module.
 */

exports = module.exports = debug;
exports.coerce = coerce;
exports.disable = disable;
exports.enable = enable;
exports.enabled = enabled;
exports.humanize = require('ms');

/**
 * The currently active debug mode names, and names to skip.
 */

exports.names = [];
exports.skips = [];

/**
 * Map of special "%n" handling functions, for the debug "format" argument.
 *
 * Valid key names are a single, lowercased letter, i.e. "n".
 */

exports.formatters = {};

/**
 * Previously assigned color.
 */

var prevColor = 0;

/**
 * Previous log timestamp.
 */

var prevTime;

/**
 * Select a color.
 *
 * @return {Number}
 * @api private
 */

function selectColor() {
  return exports.colors[prevColor++ % exports.colors.length];
}

/**
 * Create a debugger with the given `namespace`.
 *
 * @param {String} namespace
 * @return {Function}
 * @api public
 */

function debug(namespace) {

  // define the `disabled` version
  function disabled() {
  }
  disabled.enabled = false;

  // define the `enabled` version
  function enabled() {

    var self = enabled;

    // set `diff` timestamp
    var curr = +new Date();
    var ms = curr - (prevTime || curr);
    self.diff = ms;
    self.prev = prevTime;
    self.curr = curr;
    prevTime = curr;

    // add the `color` if not set
    if (null == self.useColors) self.useColors = exports.useColors();
    if (null == self.color && self.useColors) self.color = selectColor();

    var args = Array.prototype.slice.call(arguments);

    args[0] = exports.coerce(args[0]);

    if ('string' !== typeof args[0]) {
      // anything else let's inspect with %o
      args = ['%o'].concat(args);
    }

    // apply any `formatters` transformations
    var index = 0;
    args[0] = args[0].replace(/%([a-z%])/g, function(match, format) {
      // if we encounter an escaped % then don't increase the array index
      if (match === '%%') return match;
      index++;
      var formatter = exports.formatters[format];
      if ('function' === typeof formatter) {
        var val = args[index];
        match = formatter.call(self, val);

        // now we need to remove `args[index]` since it's inlined in the `format`
        args.splice(index, 1);
        index--;
      }
      return match;
    });

    if ('function' === typeof exports.formatArgs) {
      args = exports.formatArgs.apply(self, args);
    }
    var logFn = enabled.log || exports.log || console.log.bind(console);
    logFn.apply(self, args);
  }
  enabled.enabled = true;

  var fn = exports.enabled(namespace) ? enabled : disabled;

  fn.namespace = namespace;

  return fn;
}

/**
 * Enables a debug mode by namespaces. This can include modes
 * separated by a colon and wildcards.
 *
 * @param {String} namespaces
 * @api public
 */

function enable(namespaces) {
  exports.save(namespaces);

  var split = (namespaces || '').split(/[\s,]+/);
  var len = split.length;

  for (var i = 0; i < len; i++) {
    if (!split[i]) continue; // ignore empty strings
    namespaces = split[i].replace(/\*/g, '.*?');
    if (namespaces[0] === '-') {
      exports.skips.push(new RegExp('^' + namespaces.substr(1) + '$'));
    } else {
      exports.names.push(new RegExp('^' + namespaces + '$'));
    }
  }
}

/**
 * Disable debug output.
 *
 * @api public
 */

function disable() {
  exports.enable('');
}

/**
 * Returns true if the given mode name is enabled, false otherwise.
 *
 * @param {String} name
 * @return {Boolean}
 * @api public
 */

function enabled(name) {
  var i, len;
  for (i = 0, len = exports.skips.length; i < len; i++) {
    if (exports.skips[i].test(name)) {
      return false;
    }
  }
  for (i = 0, len = exports.names.length; i < len; i++) {
    if (exports.names[i].test(name)) {
      return true;
    }
  }
  return false;
}

/**
 * Coerce `val`.
 *
 * @param {Mixed} val
 * @return {Mixed}
 * @api private
 */

function coerce(val) {
  if (val instanceof Error) return val.stack || val.message;
  return val;
}

},{"ms":49}],49:[function(require,module,exports){
/**
 * Helpers.
 */

var s = 1000;
var m = s * 60;
var h = m * 60;
var d = h * 24;
var y = d * 365.25;

/**
 * Parse or format the given `val`.
 *
 * Options:
 *
 *  - `long` verbose formatting [false]
 *
 * @param {String|Number} val
 * @param {Object} options
 * @return {String|Number}
 * @api public
 */

module.exports = function(val, options){
  options = options || {};
  if ('string' == typeof val) return parse(val);
  return options.long
    ? long(val)
    : short(val);
};

/**
 * Parse the given `str` and return milliseconds.
 *
 * @param {String} str
 * @return {Number}
 * @api private
 */

function parse(str) {
  str = '' + str;
  if (str.length > 10000) return;
  var match = /^((?:\d+)?\.?\d+) *(milliseconds?|msecs?|ms|seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|days?|d|years?|yrs?|y)?$/i.exec(str);
  if (!match) return;
  var n = parseFloat(match[1]);
  var type = (match[2] || 'ms').toLowerCase();
  switch (type) {
    case 'years':
    case 'year':
    case 'yrs':
    case 'yr':
    case 'y':
      return n * y;
    case 'days':
    case 'day':
    case 'd':
      return n * d;
    case 'hours':
    case 'hour':
    case 'hrs':
    case 'hr':
    case 'h':
      return n * h;
    case 'minutes':
    case 'minute':
    case 'mins':
    case 'min':
    case 'm':
      return n * m;
    case 'seconds':
    case 'second':
    case 'secs':
    case 'sec':
    case 's':
      return n * s;
    case 'milliseconds':
    case 'millisecond':
    case 'msecs':
    case 'msec':
    case 'ms':
      return n;
  }
}

/**
 * Short format for `ms`.
 *
 * @param {Number} ms
 * @return {String}
 * @api private
 */

function short(ms) {
  if (ms >= d) return Math.round(ms / d) + 'd';
  if (ms >= h) return Math.round(ms / h) + 'h';
  if (ms >= m) return Math.round(ms / m) + 'm';
  if (ms >= s) return Math.round(ms / s) + 's';
  return ms + 'ms';
}

/**
 * Long format for `ms`.
 *
 * @param {Number} ms
 * @return {String}
 * @api private
 */

function long(ms) {
  return plural(ms, d, 'day')
    || plural(ms, h, 'hour')
    || plural(ms, m, 'minute')
    || plural(ms, s, 'second')
    || ms + ' ms';
}

/**
 * Pluralization helper.
 */

function plural(ms, n, name) {
  if (ms < n) return;
  if (ms < n * 1.5) return Math.floor(ms / n) + ' ' + name;
  return Math.ceil(ms / n) + ' ' + name + 's';
}

},{}],50:[function(require,module,exports){

/**
 * Module dependencies.
 */

try {
  var type = require('type');
} catch (err) {
  var type = require('component-type');
}

var toFunction = require('to-function');

/**
 * HOP reference.
 */

var has = Object.prototype.hasOwnProperty;

/**
 * Iterate the given `obj` and invoke `fn(val, i)`
 * in optional context `ctx`.
 *
 * @param {String|Array|Object} obj
 * @param {Function} fn
 * @param {Object} [ctx]
 * @api public
 */

module.exports = function(obj, fn, ctx){
  fn = toFunction(fn);
  ctx = ctx || this;
  switch (type(obj)) {
    case 'array':
      return array(obj, fn, ctx);
    case 'object':
      if ('number' == typeof obj.length) return array(obj, fn, ctx);
      return object(obj, fn, ctx);
    case 'string':
      return string(obj, fn, ctx);
  }
};

/**
 * Iterate string chars.
 *
 * @param {String} obj
 * @param {Function} fn
 * @param {Object} ctx
 * @api private
 */

function string(obj, fn, ctx) {
  for (var i = 0; i < obj.length; ++i) {
    fn.call(ctx, obj.charAt(i), i);
  }
}

/**
 * Iterate object keys.
 *
 * @param {Object} obj
 * @param {Function} fn
 * @param {Object} ctx
 * @api private
 */

function object(obj, fn, ctx) {
  for (var key in obj) {
    if (has.call(obj, key)) {
      fn.call(ctx, key, obj[key]);
    }
  }
}

/**
 * Iterate array-ish.
 *
 * @param {Array|Object} obj
 * @param {Function} fn
 * @param {Object} ctx
 * @api private
 */

function array(obj, fn, ctx) {
  for (var i = 0; i < obj.length; ++i) {
    fn.call(ctx, obj[i], i);
  }
}

},{"component-type":51,"to-function":95,"type":51}],51:[function(require,module,exports){

/**
 * toString ref.
 */

var toString = Object.prototype.toString;

/**
 * Return the type of `val`.
 *
 * @param {Mixed} val
 * @return {String}
 * @api public
 */

module.exports = function(val){
  switch (toString.call(val)) {
    case '[object Function]': return 'function';
    case '[object Date]': return 'date';
    case '[object RegExp]': return 'regexp';
    case '[object Arguments]': return 'arguments';
    case '[object Array]': return 'array';
    case '[object String]': return 'string';
  }

  if (val === null) return 'null';
  if (val === undefined) return 'undefined';
  if (val && val.nodeType === 1) return 'element';
  if (val === Object(val)) return 'object';

  return typeof val;
};

},{}],52:[function(require,module,exports){

/**
 * Expose `Emitter`.
 */

if (typeof module !== 'undefined') {
  module.exports = Emitter;
}

/**
 * Initialize a new `Emitter`.
 *
 * @api public
 */

function Emitter(obj) {
  if (obj) return mixin(obj);
};

/**
 * Mixin the emitter properties.
 *
 * @param {Object} obj
 * @return {Object}
 * @api private
 */

function mixin(obj) {
  for (var key in Emitter.prototype) {
    obj[key] = Emitter.prototype[key];
  }
  return obj;
}

/**
 * Listen on the given `event` with `fn`.
 *
 * @param {String} event
 * @param {Function} fn
 * @return {Emitter}
 * @api public
 */

Emitter.prototype.on =
Emitter.prototype.addEventListener = function(event, fn){
  this._callbacks = this._callbacks || {};
  (this._callbacks['$' + event] = this._callbacks['$' + event] || [])
    .push(fn);
  return this;
};

/**
 * Adds an `event` listener that will be invoked a single
 * time then automatically removed.
 *
 * @param {String} event
 * @param {Function} fn
 * @return {Emitter}
 * @api public
 */

Emitter.prototype.once = function(event, fn){
  function on() {
    this.off(event, on);
    fn.apply(this, arguments);
  }

  on.fn = fn;
  this.on(event, on);
  return this;
};

/**
 * Remove the given callback for `event` or all
 * registered callbacks.
 *
 * @param {String} event
 * @param {Function} fn
 * @return {Emitter}
 * @api public
 */

Emitter.prototype.off =
Emitter.prototype.removeListener =
Emitter.prototype.removeAllListeners =
Emitter.prototype.removeEventListener = function(event, fn){
  this._callbacks = this._callbacks || {};

  // all
  if (0 == arguments.length) {
    this._callbacks = {};
    return this;
  }

  // specific event
  var callbacks = this._callbacks['$' + event];
  if (!callbacks) return this;

  // remove all handlers
  if (1 == arguments.length) {
    delete this._callbacks['$' + event];
    return this;
  }

  // remove specific handler
  var cb;
  for (var i = 0; i < callbacks.length; i++) {
    cb = callbacks[i];
    if (cb === fn || cb.fn === fn) {
      callbacks.splice(i, 1);
      break;
    }
  }

  // Remove event specific arrays for event types that no
  // one is subscribed for to avoid memory leak.
  if (callbacks.length === 0) {
    delete this._callbacks['$' + event];
  }

  return this;
};

/**
 * Emit `event` with the given args.
 *
 * @param {String} event
 * @param {Mixed} ...
 * @return {Emitter}
 */

Emitter.prototype.emit = function(event){
  this._callbacks = this._callbacks || {};

  var args = new Array(arguments.length - 1)
    , callbacks = this._callbacks['$' + event];

  for (var i = 1; i < arguments.length; i++) {
    args[i - 1] = arguments[i];
  }

  if (callbacks) {
    callbacks = callbacks.slice(0);
    for (var i = 0, len = callbacks.length; i < len; ++i) {
      callbacks[i].apply(this, args);
    }
  }

  return this;
};

/**
 * Return array of callbacks for `event`.
 *
 * @param {String} event
 * @return {Array}
 * @api public
 */

Emitter.prototype.listeners = function(event){
  this._callbacks = this._callbacks || {};
  return this._callbacks['$' + event] || [];
};

/**
 * Check if this emitter has `event` handlers.
 *
 * @param {String} event
 * @return {Boolean}
 * @api public
 */

Emitter.prototype.hasListeners = function(event){
  return !! this.listeners(event).length;
};

},{}],53:[function(require,module,exports){
var bind = window.addEventListener ? 'addEventListener' : 'attachEvent',
    unbind = window.removeEventListener ? 'removeEventListener' : 'detachEvent',
    prefix = bind !== 'addEventListener' ? 'on' : '';

/**
 * Bind `el` event `type` to `fn`.
 *
 * @param {Element} el
 * @param {String} type
 * @param {Function} fn
 * @param {Boolean} capture
 * @return {Function}
 * @api public
 */

exports.bind = function(el, type, fn, capture){
  el[bind](prefix + type, fn, capture || false);
  return fn;
};

/**
 * Unbind `el` event `type`'s callback `fn`.
 *
 * @param {Element} el
 * @param {String} type
 * @param {Function} fn
 * @param {Boolean} capture
 * @return {Function}
 * @api public
 */

exports.unbind = function(el, type, fn, capture){
  el[unbind](prefix + type, fn, capture || false);
  return fn;
};
},{}],54:[function(require,module,exports){
/**
 * Global Names
 */

var globals = /\b(Array|Date|Object|Math|JSON)\b/g;

/**
 * Return immediate identifiers parsed from `str`.
 *
 * @param {String} str
 * @param {String|Function} map function or prefix
 * @return {Array}
 * @api public
 */

module.exports = function(str, fn){
  var p = unique(props(str));
  if (fn && 'string' == typeof fn) fn = prefixed(fn);
  if (fn) return map(str, p, fn);
  return p;
};

/**
 * Return immediate identifiers in `str`.
 *
 * @param {String} str
 * @return {Array}
 * @api private
 */

function props(str) {
  return str
    .replace(/\.\w+|\w+ *\(|"[^"]*"|'[^']*'|\/([^/]+)\//g, '')
    .replace(globals, '')
    .match(/[a-zA-Z_]\w*/g)
    || [];
}

/**
 * Return `str` with `props` mapped with `fn`.
 *
 * @param {String} str
 * @param {Array} props
 * @param {Function} fn
 * @return {String}
 * @api private
 */

function map(str, props, fn) {
  var re = /\.\w+|\w+ *\(|"[^"]*"|'[^']*'|\/([^/]+)\/|[a-zA-Z_]\w*/g;
  return str.replace(re, function(_){
    if ('(' == _[_.length - 1]) return fn(_);
    if (!~props.indexOf(_)) return _;
    return fn(_);
  });
}

/**
 * Return unique array.
 *
 * @param {Array} arr
 * @return {Array}
 * @api private
 */

function unique(arr) {
  var ret = [];

  for (var i = 0; i < arr.length; i++) {
    if (~ret.indexOf(arr[i])) continue;
    ret.push(arr[i]);
  }

  return ret;
}

/**
 * Map with prefix `str`.
 */

function prefixed(str) {
  return function(_){
    return str + _;
  };
}

},{}],55:[function(require,module,exports){

/**
 * Module dependencies.
 */

var trim = require('trim');
var type = require('type');

var pattern = /(\w+)\[(\d+)\]/

/**
 * Safely encode the given string
 * 
 * @param {String} str
 * @return {String}
 * @api private
 */

var encode = function(str) {
  try {
    return encodeURIComponent(str);
  } catch (e) {
    return str;
  }
};

/**
 * Safely decode the string
 * 
 * @param {String} str
 * @return {String}
 * @api private
 */

var decode = function(str) {
  try {
    return decodeURIComponent(str.replace(/\+/g, ' '));
  } catch (e) {
    return str;
  }
}

/**
 * Parse the given query `str`.
 *
 * @param {String} str
 * @return {Object}
 * @api public
 */

exports.parse = function(str){
  if ('string' != typeof str) return {};

  str = trim(str);
  if ('' == str) return {};
  if ('?' == str.charAt(0)) str = str.slice(1);

  var obj = {};
  var pairs = str.split('&');
  for (var i = 0; i < pairs.length; i++) {
    var parts = pairs[i].split('=');
    var key = decode(parts[0]);
    var m;

    if (m = pattern.exec(key)) {
      obj[m[1]] = obj[m[1]] || [];
      obj[m[1]][m[2]] = decode(parts[1]);
      continue;
    }

    obj[parts[0]] = null == parts[1]
      ? ''
      : decode(parts[1]);
  }

  return obj;
};

/**
 * Stringify the given `obj`.
 *
 * @param {Object} obj
 * @return {String}
 * @api public
 */

exports.stringify = function(obj){
  if (!obj) return '';
  var pairs = [];

  for (var key in obj) {
    var value = obj[key];

    if ('array' == type(value)) {
      for (var i = 0; i < value.length; ++i) {
        pairs.push(encode(key + '[' + i + ']') + '=' + encode(value[i]));
      }
      continue;
    }

    pairs.push(encode(key) + '=' + encode(obj[key]));
  }

  return pairs.join('&');
};

},{"trim":97,"type":56}],56:[function(require,module,exports){
/**
 * toString ref.
 */

var toString = Object.prototype.toString;

/**
 * Return the type of `val`.
 *
 * @param {Mixed} val
 * @return {String}
 * @api public
 */

module.exports = function(val){
  switch (toString.call(val)) {
    case '[object Date]': return 'date';
    case '[object RegExp]': return 'regexp';
    case '[object Arguments]': return 'arguments';
    case '[object Array]': return 'array';
    case '[object Error]': return 'error';
  }

  if (val === null) return 'null';
  if (val === undefined) return 'undefined';
  if (val !== val) return 'nan';
  if (val && val.nodeType === 1) return 'element';

  val = val.valueOf
    ? val.valueOf()
    : Object.prototype.valueOf.apply(val)

  return typeof val;
};

},{}],57:[function(require,module,exports){
/**
 * toString ref.
 */

var toString = Object.prototype.toString;

/**
 * Return the type of `val`.
 *
 * @param {Mixed} val
 * @return {String}
 * @api public
 */

module.exports = function(val){
  switch (toString.call(val)) {
    case '[object Date]': return 'date';
    case '[object RegExp]': return 'regexp';
    case '[object Arguments]': return 'arguments';
    case '[object Array]': return 'array';
    case '[object Error]': return 'error';
  }

  if (val === null) return 'null';
  if (val === undefined) return 'undefined';
  if (val !== val) return 'nan';
  if (val && val.nodeType === 1) return 'element';

  if (isBuffer(val)) return 'buffer';

  val = val.valueOf
    ? val.valueOf()
    : Object.prototype.valueOf.apply(val);

  return typeof val;
};

// code borrowed from https://github.com/feross/is-buffer/blob/master/index.js
function isBuffer(obj) {
  return !!(obj != null &&
    (obj._isBuffer || // For Safari 5-7 (missing Object.prototype.constructor)
      (obj.constructor &&
      typeof obj.constructor.isBuffer === 'function' &&
      obj.constructor.isBuffer(obj))
    ))
}

},{}],58:[function(require,module,exports){

/**
 * Parse the given `url`.
 *
 * @param {String} str
 * @return {Object}
 * @api public
 */

exports.parse = function(url){
  var a = document.createElement('a');
  a.href = url;
  return {
    href: a.href,
    host: a.host || location.host,
    port: ('0' === a.port || '' === a.port) ? port(a.protocol) : a.port,
    hash: a.hash,
    hostname: a.hostname || location.hostname,
    pathname: a.pathname.charAt(0) != '/' ? '/' + a.pathname : a.pathname,
    protocol: !a.protocol || ':' == a.protocol ? location.protocol : a.protocol,
    search: a.search,
    query: a.search.slice(1)
  };
};

/**
 * Check if `url` is absolute.
 *
 * @param {String} url
 * @return {Boolean}
 * @api public
 */

exports.isAbsolute = function(url){
  return 0 == url.indexOf('//') || !!~url.indexOf('://');
};

/**
 * Check if `url` is relative.
 *
 * @param {String} url
 * @return {Boolean}
 * @api public
 */

exports.isRelative = function(url){
  return !exports.isAbsolute(url);
};

/**
 * Check if `url` is cross domain.
 *
 * @param {String} url
 * @return {Boolean}
 * @api public
 */

exports.isCrossDomain = function(url){
  url = exports.parse(url);
  var location = exports.parse(window.location.href);
  return url.hostname !== location.hostname
    || url.port !== location.port
    || url.protocol !== location.protocol;
};

/**
 * Return default port for `protocol`.
 *
 * @param  {String} protocol
 * @return {String}
 * @api private
 */
function port (protocol){
  switch (protocol) {
    case 'http:':
      return 80;
    case 'https:':
      return 443;
    default:
      return location.port;
  }
}

},{}],59:[function(require,module,exports){
(function (process){(function (){
/**
 * This is the web browser implementation of `debug()`.
 *
 * Expose `debug()` as the module.
 */

exports = module.exports = require('./debug');
exports.log = log;
exports.formatArgs = formatArgs;
exports.save = save;
exports.load = load;
exports.useColors = useColors;
exports.storage = 'undefined' != typeof chrome
               && 'undefined' != typeof chrome.storage
                  ? chrome.storage.local
                  : localstorage();

/**
 * Colors.
 */

exports.colors = [
  'lightseagreen',
  'forestgreen',
  'goldenrod',
  'dodgerblue',
  'darkorchid',
  'crimson'
];

/**
 * Currently only WebKit-based Web Inspectors, Firefox >= v31,
 * and the Firebug extension (any Firefox version) are known
 * to support "%c" CSS customizations.
 *
 * TODO: add a `localStorage` variable to explicitly enable/disable colors
 */

function useColors() {
  // NB: In an Electron preload script, document will be defined but not fully
  // initialized. Since we know we're in Chrome, we'll just detect this case
  // explicitly
  if (typeof window !== 'undefined' && window.process && window.process.type === 'renderer') {
    return true;
  }

  // is webkit? http://stackoverflow.com/a/16459606/376773
  // document is undefined in react-native: https://github.com/facebook/react-native/pull/1632
  return (typeof document !== 'undefined' && document.documentElement && document.documentElement.style && document.documentElement.style.WebkitAppearance) ||
    // is firebug? http://stackoverflow.com/a/398120/376773
    (typeof window !== 'undefined' && window.console && (window.console.firebug || (window.console.exception && window.console.table))) ||
    // is firefox >= v31?
    // https://developer.mozilla.org/en-US/docs/Tools/Web_Console#Styling_messages
    (typeof navigator !== 'undefined' && navigator.userAgent && navigator.userAgent.toLowerCase().match(/firefox\/(\d+)/) && parseInt(RegExp.$1, 10) >= 31) ||
    // double check webkit in userAgent just in case we are in a worker
    (typeof navigator !== 'undefined' && navigator.userAgent && navigator.userAgent.toLowerCase().match(/applewebkit\/(\d+)/));
}

/**
 * Map %j to `JSON.stringify()`, since no Web Inspectors do that by default.
 */

exports.formatters.j = function(v) {
  try {
    return JSON.stringify(v);
  } catch (err) {
    return '[UnexpectedJSONParseError]: ' + err.message;
  }
};


/**
 * Colorize log arguments if enabled.
 *
 * @api public
 */

function formatArgs(args) {
  var useColors = this.useColors;

  args[0] = (useColors ? '%c' : '')
    + this.namespace
    + (useColors ? ' %c' : ' ')
    + args[0]
    + (useColors ? '%c ' : ' ')
    + '+' + exports.humanize(this.diff);

  if (!useColors) return;

  var c = 'color: ' + this.color;
  args.splice(1, 0, c, 'color: inherit')

  // the final "%c" is somewhat tricky, because there could be other
  // arguments passed either before or after the %c, so we need to
  // figure out the correct index to insert the CSS into
  var index = 0;
  var lastC = 0;
  args[0].replace(/%[a-zA-Z%]/g, function(match) {
    if ('%%' === match) return;
    index++;
    if ('%c' === match) {
      // we only are interested in the *last* %c
      // (the user may have provided their own)
      lastC = index;
    }
  });

  args.splice(lastC, 0, c);
}

/**
 * Invokes `console.log()` when available.
 * No-op when `console.log` is not a "function".
 *
 * @api public
 */

function log() {
  // this hackery is required for IE8/9, where
  // the `console.log` function doesn't have 'apply'
  return 'object' === typeof console
    && console.log
    && Function.prototype.apply.call(console.log, console, arguments);
}

/**
 * Save `namespaces`.
 *
 * @param {String} namespaces
 * @api private
 */

function save(namespaces) {
  try {
    if (null == namespaces) {
      exports.storage.removeItem('debug');
    } else {
      exports.storage.debug = namespaces;
    }
  } catch(e) {}
}

/**
 * Load `namespaces`.
 *
 * @return {String} returns the previously persisted debug modes
 * @api private
 */

function load() {
  var r;
  try {
    r = exports.storage.debug;
  } catch(e) {}

  // If debug isn't set in LS, and we're in Electron, try to load $DEBUG
  if (!r && typeof process !== 'undefined' && 'env' in process) {
    r = process.env.DEBUG;
  }

  return r;
}

/**
 * Enable namespaces listed in `localStorage.debug` initially.
 */

exports.enable(load());

/**
 * Localstorage attempts to return the localstorage.
 *
 * This is necessary because safari throws
 * when a user disables cookies/localstorage
 * and you attempt to access it.
 *
 * @return {LocalStorage}
 * @api private
 */

function localstorage() {
  try {
    return window.localStorage;
  } catch (e) {}
}

}).call(this)}).call(this,require('_process'))
},{"./debug":60,"_process":78}],60:[function(require,module,exports){

/**
 * This is the common logic for both the Node.js and web browser
 * implementations of `debug()`.
 *
 * Expose `debug()` as the module.
 */

exports = module.exports = createDebug.debug = createDebug['default'] = createDebug;
exports.coerce = coerce;
exports.disable = disable;
exports.enable = enable;
exports.enabled = enabled;
exports.humanize = require('ms');

/**
 * The currently active debug mode names, and names to skip.
 */

exports.names = [];
exports.skips = [];

/**
 * Map of special "%n" handling functions, for the debug "format" argument.
 *
 * Valid key names are a single, lower or upper-case letter, i.e. "n" and "N".
 */

exports.formatters = {};

/**
 * Previous log timestamp.
 */

var prevTime;

/**
 * Select a color.
 * @param {String} namespace
 * @return {Number}
 * @api private
 */

function selectColor(namespace) {
  var hash = 0, i;

  for (i in namespace) {
    hash  = ((hash << 5) - hash) + namespace.charCodeAt(i);
    hash |= 0; // Convert to 32bit integer
  }

  return exports.colors[Math.abs(hash) % exports.colors.length];
}

/**
 * Create a debugger with the given `namespace`.
 *
 * @param {String} namespace
 * @return {Function}
 * @api public
 */

function createDebug(namespace) {

  function debug() {
    // disabled?
    if (!debug.enabled) return;

    var self = debug;

    // set `diff` timestamp
    var curr = +new Date();
    var ms = curr - (prevTime || curr);
    self.diff = ms;
    self.prev = prevTime;
    self.curr = curr;
    prevTime = curr;

    // turn the `arguments` into a proper Array
    var args = new Array(arguments.length);
    for (var i = 0; i < args.length; i++) {
      args[i] = arguments[i];
    }

    args[0] = exports.coerce(args[0]);

    if ('string' !== typeof args[0]) {
      // anything else let's inspect with %O
      args.unshift('%O');
    }

    // apply any `formatters` transformations
    var index = 0;
    args[0] = args[0].replace(/%([a-zA-Z%])/g, function(match, format) {
      // if we encounter an escaped % then don't increase the array index
      if (match === '%%') return match;
      index++;
      var formatter = exports.formatters[format];
      if ('function' === typeof formatter) {
        var val = args[index];
        match = formatter.call(self, val);

        // now we need to remove `args[index]` since it's inlined in the `format`
        args.splice(index, 1);
        index--;
      }
      return match;
    });

    // apply env-specific formatting (colors, etc.)
    exports.formatArgs.call(self, args);

    var logFn = debug.log || exports.log || console.log.bind(console);
    logFn.apply(self, args);
  }

  debug.namespace = namespace;
  debug.enabled = exports.enabled(namespace);
  debug.useColors = exports.useColors();
  debug.color = selectColor(namespace);

  // env-specific initialization logic for debug instances
  if ('function' === typeof exports.init) {
    exports.init(debug);
  }

  return debug;
}

/**
 * Enables a debug mode by namespaces. This can include modes
 * separated by a colon and wildcards.
 *
 * @param {String} namespaces
 * @api public
 */

function enable(namespaces) {
  exports.save(namespaces);

  exports.names = [];
  exports.skips = [];

  var split = (typeof namespaces === 'string' ? namespaces : '').split(/[\s,]+/);
  var len = split.length;

  for (var i = 0; i < len; i++) {
    if (!split[i]) continue; // ignore empty strings
    namespaces = split[i].replace(/\*/g, '.*?');
    if (namespaces[0] === '-') {
      exports.skips.push(new RegExp('^' + namespaces.substr(1) + '$'));
    } else {
      exports.names.push(new RegExp('^' + namespaces + '$'));
    }
  }
}

/**
 * Disable debug output.
 *
 * @api public
 */

function disable() {
  exports.enable('');
}

/**
 * Returns true if the given mode name is enabled, false otherwise.
 *
 * @param {String} name
 * @return {Boolean}
 * @api public
 */

function enabled(name) {
  var i, len;
  for (i = 0, len = exports.skips.length; i < len; i++) {
    if (exports.skips[i].test(name)) {
      return false;
    }
  }
  for (i = 0, len = exports.names.length; i < len; i++) {
    if (exports.names[i].test(name)) {
      return true;
    }
  }
  return false;
}

/**
 * Coerce `val`.
 *
 * @param {Mixed} val
 * @return {Mixed}
 * @api private
 */

function coerce(val) {
  if (val instanceof Error) return val.stack || val.message;
  return val;
}

},{"ms":71}],61:[function(require,module,exports){

/**
 * Expose `parse`.
 */

module.exports = parse;

/**
 * Tests for browser support.
 */

var innerHTMLBug = false;
var bugTestDiv;
if (typeof document !== 'undefined') {
  bugTestDiv = document.createElement('div');
  // Setup
  bugTestDiv.innerHTML = '  <link/><table></table><a href="/a">a</a><input type="checkbox"/>';
  // Make sure that link elements get serialized correctly by innerHTML
  // This requires a wrapper element in IE
  innerHTMLBug = !bugTestDiv.getElementsByTagName('link').length;
  bugTestDiv = undefined;
}

/**
 * Wrap map from jquery.
 */

var map = {
  legend: [1, '<fieldset>', '</fieldset>'],
  tr: [2, '<table><tbody>', '</tbody></table>'],
  col: [2, '<table><tbody></tbody><colgroup>', '</colgroup></table>'],
  // for script/link/style tags to work in IE6-8, you have to wrap
  // in a div with a non-whitespace character in front, ha!
  _default: innerHTMLBug ? [1, 'X<div>', '</div>'] : [0, '', '']
};

map.td =
map.th = [3, '<table><tbody><tr>', '</tr></tbody></table>'];

map.option =
map.optgroup = [1, '<select multiple="multiple">', '</select>'];

map.thead =
map.tbody =
map.colgroup =
map.caption =
map.tfoot = [1, '<table>', '</table>'];

map.polyline =
map.ellipse =
map.polygon =
map.circle =
map.text =
map.line =
map.path =
map.rect =
map.g = [1, '<svg xmlns="http://www.w3.org/2000/svg" version="1.1">','</svg>'];

/**
 * Parse `html` and return a DOM Node instance, which could be a TextNode,
 * HTML DOM Node of some kind (<div> for example), or a DocumentFragment
 * instance, depending on the contents of the `html` string.
 *
 * @param {String} html - HTML string to "domify"
 * @param {Document} doc - The `document` instance to create the Node for
 * @return {DOMNode} the TextNode, DOM Node, or DocumentFragment instance
 * @api private
 */

function parse(html, doc) {
  if ('string' != typeof html) throw new TypeError('String expected');

  // default to the global `document` object
  if (!doc) doc = document;

  // tag name
  var m = /<([\w:]+)/.exec(html);
  if (!m) return doc.createTextNode(html);

  html = html.replace(/^\s+|\s+$/g, ''); // Remove leading/trailing whitespace

  var tag = m[1];

  // body support
  if (tag == 'body') {
    var el = doc.createElement('html');
    el.innerHTML = html;
    return el.removeChild(el.lastChild);
  }

  // wrap map
  var wrap = map[tag] || map._default;
  var depth = wrap[0];
  var prefix = wrap[1];
  var suffix = wrap[2];
  var el = doc.createElement('div');
  el.innerHTML = prefix + html + suffix;
  while (depth--) el = el.lastChild;

  // one element
  if (el.firstChild == el.lastChild) {
    return el.removeChild(el.firstChild);
  }

  // several elements
  var fragment = doc.createDocumentFragment();
  while (el.firstChild) {
    fragment.appendChild(el.removeChild(el.firstChild));
  }

  return fragment;
}

},{}],62:[function(require,module,exports){
'use strict';

var hasOwn = Object.prototype.hasOwnProperty;
var toStr = Object.prototype.toString;
var defineProperty = Object.defineProperty;
var gOPD = Object.getOwnPropertyDescriptor;

var isArray = function isArray(arr) {
	if (typeof Array.isArray === 'function') {
		return Array.isArray(arr);
	}

	return toStr.call(arr) === '[object Array]';
};

var isPlainObject = function isPlainObject(obj) {
	if (!obj || toStr.call(obj) !== '[object Object]') {
		return false;
	}

	var hasOwnConstructor = hasOwn.call(obj, 'constructor');
	var hasIsPrototypeOf = obj.constructor && obj.constructor.prototype && hasOwn.call(obj.constructor.prototype, 'isPrototypeOf');
	// Not own constructor property must be Object
	if (obj.constructor && !hasOwnConstructor && !hasIsPrototypeOf) {
		return false;
	}

	// Own properties are enumerated firstly, so to speed up,
	// if last one is own, then all properties are own.
	var key;
	for (key in obj) { /**/ }

	return typeof key === 'undefined' || hasOwn.call(obj, key);
};

// If name is '__proto__', and Object.defineProperty is available, define __proto__ as an own property on target
var setProperty = function setProperty(target, options) {
	if (defineProperty && options.name === '__proto__') {
		defineProperty(target, options.name, {
			enumerable: true,
			configurable: true,
			value: options.newValue,
			writable: true
		});
	} else {
		target[options.name] = options.newValue;
	}
};

// Return undefined instead of __proto__ if '__proto__' is not an own property
var getProperty = function getProperty(obj, name) {
	if (name === '__proto__') {
		if (!hasOwn.call(obj, name)) {
			return void 0;
		} else if (gOPD) {
			// In early versions of node, obj['__proto__'] is buggy when obj has
			// __proto__ as an own property. Object.getOwnPropertyDescriptor() works.
			return gOPD(obj, name).value;
		}
	}

	return obj[name];
};

module.exports = function extend() {
	var options, name, src, copy, copyIsArray, clone;
	var target = arguments[0];
	var i = 1;
	var length = arguments.length;
	var deep = false;

	// Handle a deep copy situation
	if (typeof target === 'boolean') {
		deep = target;
		target = arguments[1] || {};
		// skip the boolean and the target
		i = 2;
	}
	if (target == null || (typeof target !== 'object' && typeof target !== 'function')) {
		target = {};
	}

	for (; i < length; ++i) {
		options = arguments[i];
		// Only deal with non-null/undefined values
		if (options != null) {
			// Extend the base object
			for (name in options) {
				src = getProperty(target, name);
				copy = getProperty(options, name);

				// Prevent never-ending loop
				if (target !== copy) {
					// Recurse if we're merging plain objects or arrays
					if (deep && copy && (isPlainObject(copy) || (copyIsArray = isArray(copy)))) {
						if (copyIsArray) {
							copyIsArray = false;
							clone = src && isArray(src) ? src : [];
						} else {
							clone = src && isPlainObject(src) ? src : {};
						}

						// Never move original objects, clone them
						setProperty(target, { name: name, newValue: extend(deep, clone, copy) });

					// Don't bring in undefined values
					} else if (typeof copy !== 'undefined') {
						setProperty(target, { name: name, newValue: copy });
					}
				}
			}
		}
	}

	// Return the modified object
	return target;
};

},{}],63:[function(require,module,exports){

/**
 * Module exports.
 *
 * Logic borrowed from Modernizr:
 *
 *   - https://github.com/Modernizr/Modernizr/blob/master/feature-detects/cors.js
 */

try {
  module.exports = typeof XMLHttpRequest !== 'undefined' &&
    'withCredentials' in new XMLHttpRequest();
} catch (err) {
  // if XMLHttp support is disabled in IE then it will throw
  // when trying to create
  module.exports = false;
}

},{}],64:[function(require,module,exports){
if (typeof Object.create === 'function') {
  // implementation from standard node.js 'util' module
  module.exports = function inherits(ctor, superCtor) {
    if (superCtor) {
      ctor.super_ = superCtor
      ctor.prototype = Object.create(superCtor.prototype, {
        constructor: {
          value: ctor,
          enumerable: false,
          writable: true,
          configurable: true
        }
      })
    }
  };
} else {
  // old school shim for old browsers
  module.exports = function inherits(ctor, superCtor) {
    if (superCtor) {
      ctor.super_ = superCtor
      var TempCtor = function () {}
      TempCtor.prototype = superCtor.prototype
      ctor.prototype = new TempCtor()
      ctor.prototype.constructor = ctor
    }
  }
}

},{}],65:[function(require,module,exports){

module.exports = function isEmail (string) {
    return (/.+\@.+\..+/).test(string);
};
},{}],66:[function(require,module,exports){
/* globals window, HTMLElement */

'use strict';

/**!
 * is
 * the definitive JavaScript type testing library
 *
 * @copyright 2013-2014 Enrico Marino / Jordan Harband
 * @license MIT
 */

var objProto = Object.prototype;
var owns = objProto.hasOwnProperty;
var toStr = objProto.toString;
var symbolValueOf;
if (typeof Symbol === 'function') {
  symbolValueOf = Symbol.prototype.valueOf;
}
var bigIntValueOf;
if (typeof BigInt === 'function') {
  bigIntValueOf = BigInt.prototype.valueOf;
}
var isActualNaN = function (value) {
  return value !== value;
};
var NON_HOST_TYPES = {
  'boolean': 1,
  number: 1,
  string: 1,
  undefined: 1
};

var base64Regex = /^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)$/;
var hexRegex = /^[A-Fa-f0-9]+$/;

/**
 * Expose `is`
 */

var is = {};

/**
 * Test general.
 */

/**
 * is.type
 * Test if `value` is a type of `type`.
 *
 * @param {*} value value to test
 * @param {String} type type
 * @return {Boolean} true if `value` is a type of `type`, false otherwise
 * @api public
 */

is.a = is.type = function (value, type) {
  return typeof value === type;
};

/**
 * is.defined
 * Test if `value` is defined.
 *
 * @param {*} value value to test
 * @return {Boolean} true if 'value' is defined, false otherwise
 * @api public
 */

is.defined = function (value) {
  return typeof value !== 'undefined';
};

/**
 * is.empty
 * Test if `value` is empty.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is empty, false otherwise
 * @api public
 */

is.empty = function (value) {
  var type = toStr.call(value);
  var key;

  if (type === '[object Array]' || type === '[object Arguments]' || type === '[object String]') {
    return value.length === 0;
  }

  if (type === '[object Object]') {
    for (key in value) {
      if (owns.call(value, key)) {
        return false;
      }
    }
    return true;
  }

  return !value;
};

/**
 * is.equal
 * Test if `value` is equal to `other`.
 *
 * @param {*} value value to test
 * @param {*} other value to compare with
 * @return {Boolean} true if `value` is equal to `other`, false otherwise
 */

is.equal = function equal(value, other) {
  if (value === other) {
    return true;
  }

  var type = toStr.call(value);
  var key;

  if (type !== toStr.call(other)) {
    return false;
  }

  if (type === '[object Object]') {
    for (key in value) {
      if (!is.equal(value[key], other[key]) || !(key in other)) {
        return false;
      }
    }
    for (key in other) {
      if (!is.equal(value[key], other[key]) || !(key in value)) {
        return false;
      }
    }
    return true;
  }

  if (type === '[object Array]') {
    key = value.length;
    if (key !== other.length) {
      return false;
    }
    while (key--) {
      if (!is.equal(value[key], other[key])) {
        return false;
      }
    }
    return true;
  }

  if (type === '[object Function]') {
    return value.prototype === other.prototype;
  }

  if (type === '[object Date]') {
    return value.getTime() === other.getTime();
  }

  return false;
};

/**
 * is.hosted
 * Test if `value` is hosted by `host`.
 *
 * @param {*} value to test
 * @param {*} host host to test with
 * @return {Boolean} true if `value` is hosted by `host`, false otherwise
 * @api public
 */

is.hosted = function (value, host) {
  var type = typeof host[value];
  return type === 'object' ? !!host[value] : !NON_HOST_TYPES[type];
};

/**
 * is.instance
 * Test if `value` is an instance of `constructor`.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is an instance of `constructor`
 * @api public
 */

is.instance = is['instanceof'] = function (value, constructor) {
  return value instanceof constructor;
};

/**
 * is.nil / is.null
 * Test if `value` is null.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is null, false otherwise
 * @api public
 */

is.nil = is['null'] = function (value) {
  return value === null;
};

/**
 * is.undef / is.undefined
 * Test if `value` is undefined.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is undefined, false otherwise
 * @api public
 */

is.undef = is.undefined = function (value) {
  return typeof value === 'undefined';
};

/**
 * Test arguments.
 */

/**
 * is.args
 * Test if `value` is an arguments object.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is an arguments object, false otherwise
 * @api public
 */

is.args = is.arguments = function (value) {
  var isStandardArguments = toStr.call(value) === '[object Arguments]';
  var isOldArguments = !is.array(value) && is.arraylike(value) && is.object(value) && is.fn(value.callee);
  return isStandardArguments || isOldArguments;
};

/**
 * Test array.
 */

/**
 * is.array
 * Test if 'value' is an array.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is an array, false otherwise
 * @api public
 */

is.array = Array.isArray || function (value) {
  return toStr.call(value) === '[object Array]';
};

/**
 * is.arguments.empty
 * Test if `value` is an empty arguments object.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is an empty arguments object, false otherwise
 * @api public
 */
is.args.empty = function (value) {
  return is.args(value) && value.length === 0;
};

/**
 * is.array.empty
 * Test if `value` is an empty array.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is an empty array, false otherwise
 * @api public
 */
is.array.empty = function (value) {
  return is.array(value) && value.length === 0;
};

/**
 * is.arraylike
 * Test if `value` is an arraylike object.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is an arguments object, false otherwise
 * @api public
 */

is.arraylike = function (value) {
  return !!value && !is.bool(value)
    && owns.call(value, 'length')
    && isFinite(value.length)
    && is.number(value.length)
    && value.length >= 0;
};

/**
 * Test boolean.
 */

/**
 * is.bool
 * Test if `value` is a boolean.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is a boolean, false otherwise
 * @api public
 */

is.bool = is['boolean'] = function (value) {
  return toStr.call(value) === '[object Boolean]';
};

/**
 * is.false
 * Test if `value` is false.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is false, false otherwise
 * @api public
 */

is['false'] = function (value) {
  return is.bool(value) && Boolean(Number(value)) === false;
};

/**
 * is.true
 * Test if `value` is true.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is true, false otherwise
 * @api public
 */

is['true'] = function (value) {
  return is.bool(value) && Boolean(Number(value)) === true;
};

/**
 * Test date.
 */

/**
 * is.date
 * Test if `value` is a date.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is a date, false otherwise
 * @api public
 */

is.date = function (value) {
  return toStr.call(value) === '[object Date]';
};

/**
 * is.date.valid
 * Test if `value` is a valid date.
 *
 * @param {*} value value to test
 * @returns {Boolean} true if `value` is a valid date, false otherwise
 */
is.date.valid = function (value) {
  return is.date(value) && !isNaN(Number(value));
};

/**
 * Test element.
 */

/**
 * is.element
 * Test if `value` is an html element.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is an HTML Element, false otherwise
 * @api public
 */

is.element = function (value) {
  return value !== undefined
    && typeof HTMLElement !== 'undefined'
    && value instanceof HTMLElement
    && value.nodeType === 1;
};

/**
 * Test error.
 */

/**
 * is.error
 * Test if `value` is an error object.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is an error object, false otherwise
 * @api public
 */

is.error = function (value) {
  return toStr.call(value) === '[object Error]';
};

/**
 * Test function.
 */

/**
 * is.fn / is.function (deprecated)
 * Test if `value` is a function.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is a function, false otherwise
 * @api public
 */

is.fn = is['function'] = function (value) {
  var isAlert = typeof window !== 'undefined' && value === window.alert;
  if (isAlert) {
    return true;
  }
  var str = toStr.call(value);
  return str === '[object Function]' || str === '[object GeneratorFunction]' || str === '[object AsyncFunction]';
};

/**
 * Test number.
 */

/**
 * is.number
 * Test if `value` is a number.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is a number, false otherwise
 * @api public
 */

is.number = function (value) {
  return toStr.call(value) === '[object Number]';
};

/**
 * is.infinite
 * Test if `value` is positive or negative infinity.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is positive or negative Infinity, false otherwise
 * @api public
 */
is.infinite = function (value) {
  return value === Infinity || value === -Infinity;
};

/**
 * is.decimal
 * Test if `value` is a decimal number.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is a decimal number, false otherwise
 * @api public
 */

is.decimal = function (value) {
  return is.number(value) && !isActualNaN(value) && !is.infinite(value) && value % 1 !== 0;
};

/**
 * is.divisibleBy
 * Test if `value` is divisible by `n`.
 *
 * @param {Number} value value to test
 * @param {Number} n dividend
 * @return {Boolean} true if `value` is divisible by `n`, false otherwise
 * @api public
 */

is.divisibleBy = function (value, n) {
  var isDividendInfinite = is.infinite(value);
  var isDivisorInfinite = is.infinite(n);
  var isNonZeroNumber = is.number(value) && !isActualNaN(value) && is.number(n) && !isActualNaN(n) && n !== 0;
  return isDividendInfinite || isDivisorInfinite || (isNonZeroNumber && value % n === 0);
};

/**
 * is.integer
 * Test if `value` is an integer.
 *
 * @param value to test
 * @return {Boolean} true if `value` is an integer, false otherwise
 * @api public
 */

is.integer = is['int'] = function (value) {
  return is.number(value) && !isActualNaN(value) && value % 1 === 0;
};

/**
 * is.maximum
 * Test if `value` is greater than 'others' values.
 *
 * @param {Number} value value to test
 * @param {Array} others values to compare with
 * @return {Boolean} true if `value` is greater than `others` values
 * @api public
 */

is.maximum = function (value, others) {
  if (isActualNaN(value)) {
    throw new TypeError('NaN is not a valid value');
  } else if (!is.arraylike(others)) {
    throw new TypeError('second argument must be array-like');
  }
  var len = others.length;

  while (--len >= 0) {
    if (value < others[len]) {
      return false;
    }
  }

  return true;
};

/**
 * is.minimum
 * Test if `value` is less than `others` values.
 *
 * @param {Number} value value to test
 * @param {Array} others values to compare with
 * @return {Boolean} true if `value` is less than `others` values
 * @api public
 */

is.minimum = function (value, others) {
  if (isActualNaN(value)) {
    throw new TypeError('NaN is not a valid value');
  } else if (!is.arraylike(others)) {
    throw new TypeError('second argument must be array-like');
  }
  var len = others.length;

  while (--len >= 0) {
    if (value > others[len]) {
      return false;
    }
  }

  return true;
};

/**
 * is.nan
 * Test if `value` is not a number.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is not a number, false otherwise
 * @api public
 */

is.nan = function (value) {
  return !is.number(value) || value !== value;
};

/**
 * is.even
 * Test if `value` is an even number.
 *
 * @param {Number} value value to test
 * @return {Boolean} true if `value` is an even number, false otherwise
 * @api public
 */

is.even = function (value) {
  return is.infinite(value) || (is.number(value) && value === value && value % 2 === 0);
};

/**
 * is.odd
 * Test if `value` is an odd number.
 *
 * @param {Number} value value to test
 * @return {Boolean} true if `value` is an odd number, false otherwise
 * @api public
 */

is.odd = function (value) {
  return is.infinite(value) || (is.number(value) && value === value && value % 2 !== 0);
};

/**
 * is.ge
 * Test if `value` is greater than or equal to `other`.
 *
 * @param {Number} value value to test
 * @param {Number} other value to compare with
 * @return {Boolean}
 * @api public
 */

is.ge = function (value, other) {
  if (isActualNaN(value) || isActualNaN(other)) {
    throw new TypeError('NaN is not a valid value');
  }
  return !is.infinite(value) && !is.infinite(other) && value >= other;
};

/**
 * is.gt
 * Test if `value` is greater than `other`.
 *
 * @param {Number} value value to test
 * @param {Number} other value to compare with
 * @return {Boolean}
 * @api public
 */

is.gt = function (value, other) {
  if (isActualNaN(value) || isActualNaN(other)) {
    throw new TypeError('NaN is not a valid value');
  }
  return !is.infinite(value) && !is.infinite(other) && value > other;
};

/**
 * is.le
 * Test if `value` is less than or equal to `other`.
 *
 * @param {Number} value value to test
 * @param {Number} other value to compare with
 * @return {Boolean} if 'value' is less than or equal to 'other'
 * @api public
 */

is.le = function (value, other) {
  if (isActualNaN(value) || isActualNaN(other)) {
    throw new TypeError('NaN is not a valid value');
  }
  return !is.infinite(value) && !is.infinite(other) && value <= other;
};

/**
 * is.lt
 * Test if `value` is less than `other`.
 *
 * @param {Number} value value to test
 * @param {Number} other value to compare with
 * @return {Boolean} if `value` is less than `other`
 * @api public
 */

is.lt = function (value, other) {
  if (isActualNaN(value) || isActualNaN(other)) {
    throw new TypeError('NaN is not a valid value');
  }
  return !is.infinite(value) && !is.infinite(other) && value < other;
};

/**
 * is.within
 * Test if `value` is within `start` and `finish`.
 *
 * @param {Number} value value to test
 * @param {Number} start lower bound
 * @param {Number} finish upper bound
 * @return {Boolean} true if 'value' is is within 'start' and 'finish'
 * @api public
 */
is.within = function (value, start, finish) {
  if (isActualNaN(value) || isActualNaN(start) || isActualNaN(finish)) {
    throw new TypeError('NaN is not a valid value');
  } else if (!is.number(value) || !is.number(start) || !is.number(finish)) {
    throw new TypeError('all arguments must be numbers');
  }
  var isAnyInfinite = is.infinite(value) || is.infinite(start) || is.infinite(finish);
  return isAnyInfinite || (value >= start && value <= finish);
};

/**
 * Test object.
 */

/**
 * is.object
 * Test if `value` is an object.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is an object, false otherwise
 * @api public
 */
is.object = function (value) {
  return toStr.call(value) === '[object Object]';
};

/**
 * is.primitive
 * Test if `value` is a primitive.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is a primitive, false otherwise
 * @api public
 */
is.primitive = function isPrimitive(value) {
  if (!value) {
    return true;
  }
  if (typeof value === 'object' || is.object(value) || is.fn(value) || is.array(value)) {
    return false;
  }
  return true;
};

/**
 * is.hash
 * Test if `value` is a hash - a plain object literal.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is a hash, false otherwise
 * @api public
 */

is.hash = function (value) {
  return is.object(value) && value.constructor === Object && !value.nodeType && !value.setInterval;
};

/**
 * Test regexp.
 */

/**
 * is.regexp
 * Test if `value` is a regular expression.
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is a regexp, false otherwise
 * @api public
 */

is.regexp = function (value) {
  return toStr.call(value) === '[object RegExp]';
};

/**
 * Test string.
 */

/**
 * is.string
 * Test if `value` is a string.
 *
 * @param {*} value value to test
 * @return {Boolean} true if 'value' is a string, false otherwise
 * @api public
 */

is.string = function (value) {
  return toStr.call(value) === '[object String]';
};

/**
 * Test base64 string.
 */

/**
 * is.base64
 * Test if `value` is a valid base64 encoded string.
 *
 * @param {*} value value to test
 * @return {Boolean} true if 'value' is a base64 encoded string, false otherwise
 * @api public
 */

is.base64 = function (value) {
  return is.string(value) && (!value.length || base64Regex.test(value));
};

/**
 * Test base64 string.
 */

/**
 * is.hex
 * Test if `value` is a valid hex encoded string.
 *
 * @param {*} value value to test
 * @return {Boolean} true if 'value' is a hex encoded string, false otherwise
 * @api public
 */

is.hex = function (value) {
  return is.string(value) && (!value.length || hexRegex.test(value));
};

/**
 * is.symbol
 * Test if `value` is an ES6 Symbol
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is a Symbol, false otherise
 * @api public
 */

is.symbol = function (value) {
  return typeof Symbol === 'function' && toStr.call(value) === '[object Symbol]' && typeof symbolValueOf.call(value) === 'symbol';
};

/**
 * is.bigint
 * Test if `value` is an ES-proposed BigInt
 *
 * @param {*} value value to test
 * @return {Boolean} true if `value` is a BigInt, false otherise
 * @api public
 */

is.bigint = function (value) {
  // eslint-disable-next-line valid-typeof
  return typeof BigInt === 'function' && toStr.call(value) === '[object BigInt]' && typeof bigIntValueOf.call(value) === 'bigint';
};

module.exports = is;

},{}],67:[function(require,module,exports){
(function (global){(function (){
/*! JSON v3.3.2 | https://bestiejs.github.io/json3 | Copyright 2012-2015, Kit Cambridge, Benjamin Tan | http://kit.mit-license.org */
;(function () {
  // Detect the `define` function exposed by asynchronous module loaders. The
  // strict `define` check is necessary for compatibility with `r.js`.
  var isLoader = typeof define === "function" && define.amd;

  // A set of types used to distinguish objects from primitives.
  var objectTypes = {
    "function": true,
    "object": true
  };

  // Detect the `exports` object exposed by CommonJS implementations.
  var freeExports = objectTypes[typeof exports] && exports && !exports.nodeType && exports;

  // Use the `global` object exposed by Node (including Browserify via
  // `insert-module-globals`), Narwhal, and Ringo as the default context,
  // and the `window` object in browsers. Rhino exports a `global` function
  // instead.
  var root = objectTypes[typeof window] && window || this,
      freeGlobal = freeExports && objectTypes[typeof module] && module && !module.nodeType && typeof global == "object" && global;

  if (freeGlobal && (freeGlobal.global === freeGlobal || freeGlobal.window === freeGlobal || freeGlobal.self === freeGlobal)) {
    root = freeGlobal;
  }

  // Public: Initializes JSON 3 using the given `context` object, attaching the
  // `stringify` and `parse` functions to the specified `exports` object.
  function runInContext(context, exports) {
    context || (context = root.Object());
    exports || (exports = root.Object());

    // Native constructor aliases.
    var Number = context.Number || root.Number,
        String = context.String || root.String,
        Object = context.Object || root.Object,
        Date = context.Date || root.Date,
        SyntaxError = context.SyntaxError || root.SyntaxError,
        TypeError = context.TypeError || root.TypeError,
        Math = context.Math || root.Math,
        nativeJSON = context.JSON || root.JSON;

    // Delegate to the native `stringify` and `parse` implementations.
    if (typeof nativeJSON == "object" && nativeJSON) {
      exports.stringify = nativeJSON.stringify;
      exports.parse = nativeJSON.parse;
    }

    // Convenience aliases.
    var objectProto = Object.prototype,
        getClass = objectProto.toString,
        isProperty = objectProto.hasOwnProperty,
        undefined;

    // Internal: Contains `try...catch` logic used by other functions.
    // This prevents other functions from being deoptimized.
    function attempt(func, errorFunc) {
      try {
        func();
      } catch (exception) {
        if (errorFunc) {
          errorFunc();
        }
      }
    }

    // Test the `Date#getUTC*` methods. Based on work by @Yaffle.
    var isExtended = new Date(-3509827334573292);
    attempt(function () {
      // The `getUTCFullYear`, `Month`, and `Date` methods return nonsensical
      // results for certain dates in Opera >= 10.53.
      isExtended = isExtended.getUTCFullYear() == -109252 && isExtended.getUTCMonth() === 0 && isExtended.getUTCDate() === 1 &&
        isExtended.getUTCHours() == 10 && isExtended.getUTCMinutes() == 37 && isExtended.getUTCSeconds() == 6 && isExtended.getUTCMilliseconds() == 708;
    });

    // Internal: Determines whether the native `JSON.stringify` and `parse`
    // implementations are spec-compliant. Based on work by Ken Snyder.
    function has(name) {
      if (has[name] != null) {
        // Return cached feature test result.
        return has[name];
      }
      var isSupported;
      if (name == "bug-string-char-index") {
        // IE <= 7 doesn't support accessing string characters using square
        // bracket notation. IE 8 only supports this for primitives.
        isSupported = "a"[0] != "a";
      } else if (name == "json") {
        // Indicates whether both `JSON.stringify` and `JSON.parse` are
        // supported.
        isSupported = has("json-stringify") && has("date-serialization") && has("json-parse");
      } else if (name == "date-serialization") {
        // Indicates whether `Date`s can be serialized accurately by `JSON.stringify`.
        isSupported = has("json-stringify") && isExtended;
        if (isSupported) {
          var stringify = exports.stringify;
          attempt(function () {
            isSupported =
              // JSON 2, Prototype <= 1.7, and older WebKit builds incorrectly
              // serialize extended years.
              stringify(new Date(-8.64e15)) == '"-271821-04-20T00:00:00.000Z"' &&
              // The milliseconds are optional in ES 5, but required in 5.1.
              stringify(new Date(8.64e15)) == '"+275760-09-13T00:00:00.000Z"' &&
              // Firefox <= 11.0 incorrectly serializes years prior to 0 as negative
              // four-digit years instead of six-digit years. Credits: @Yaffle.
              stringify(new Date(-621987552e5)) == '"-000001-01-01T00:00:00.000Z"' &&
              // Safari <= 5.1.5 and Opera >= 10.53 incorrectly serialize millisecond
              // values less than 1000. Credits: @Yaffle.
              stringify(new Date(-1)) == '"1969-12-31T23:59:59.999Z"';
          });
        }
      } else {
        var value, serialized = '{"a":[1,true,false,null,"\\u0000\\b\\n\\f\\r\\t"]}';
        // Test `JSON.stringify`.
        if (name == "json-stringify") {
          var stringify = exports.stringify, stringifySupported = typeof stringify == "function";
          if (stringifySupported) {
            // A test function object with a custom `toJSON` method.
            (value = function () {
              return 1;
            }).toJSON = value;
            attempt(function () {
              stringifySupported =
                // Firefox 3.1b1 and b2 serialize string, number, and boolean
                // primitives as object literals.
                stringify(0) === "0" &&
                // FF 3.1b1, b2, and JSON 2 serialize wrapped primitives as object
                // literals.
                stringify(new Number()) === "0" &&
                stringify(new String()) == '""' &&
                // FF 3.1b1, 2 throw an error if the value is `null`, `undefined`, or
                // does not define a canonical JSON representation (this applies to
                // objects with `toJSON` properties as well, *unless* they are nested
                // within an object or array).
                stringify(getClass) === undefined &&
                // IE 8 serializes `undefined` as `"undefined"`. Safari <= 5.1.7 and
                // FF 3.1b3 pass this test.
                stringify(undefined) === undefined &&
                // Safari <= 5.1.7 and FF 3.1b3 throw `Error`s and `TypeError`s,
                // respectively, if the value is omitted entirely.
                stringify() === undefined &&
                // FF 3.1b1, 2 throw an error if the given value is not a number,
                // string, array, object, Boolean, or `null` literal. This applies to
                // objects with custom `toJSON` methods as well, unless they are nested
                // inside object or array literals. YUI 3.0.0b1 ignores custom `toJSON`
                // methods entirely.
                stringify(value) === "1" &&
                stringify([value]) == "[1]" &&
                // Prototype <= 1.6.1 serializes `[undefined]` as `"[]"` instead of
                // `"[null]"`.
                stringify([undefined]) == "[null]" &&
                // YUI 3.0.0b1 fails to serialize `null` literals.
                stringify(null) == "null" &&
                // FF 3.1b1, 2 halts serialization if an array contains a function:
                // `[1, true, getClass, 1]` serializes as "[1,true,],". FF 3.1b3
                // elides non-JSON values from objects and arrays, unless they
                // define custom `toJSON` methods.
                stringify([undefined, getClass, null]) == "[null,null,null]" &&
                // Simple serialization test. FF 3.1b1 uses Unicode escape sequences
                // where character escape codes are expected (e.g., `\b` => `\u0008`).
                stringify({ "a": [value, true, false, null, "\x00\b\n\f\r\t"] }) == serialized &&
                // FF 3.1b1 and b2 ignore the `filter` and `width` arguments.
                stringify(null, value) === "1" &&
                stringify([1, 2], null, 1) == "[\n 1,\n 2\n]";
            }, function () {
              stringifySupported = false;
            });
          }
          isSupported = stringifySupported;
        }
        // Test `JSON.parse`.
        if (name == "json-parse") {
          var parse = exports.parse, parseSupported;
          if (typeof parse == "function") {
            attempt(function () {
              // FF 3.1b1, b2 will throw an exception if a bare literal is provided.
              // Conforming implementations should also coerce the initial argument to
              // a string prior to parsing.
              if (parse("0") === 0 && !parse(false)) {
                // Simple parsing test.
                value = parse(serialized);
                parseSupported = value["a"].length == 5 && value["a"][0] === 1;
                if (parseSupported) {
                  attempt(function () {
                    // Safari <= 5.1.2 and FF 3.1b1 allow unescaped tabs in strings.
                    parseSupported = !parse('"\t"');
                  });
                  if (parseSupported) {
                    attempt(function () {
                      // FF 4.0 and 4.0.1 allow leading `+` signs and leading
                      // decimal points. FF 4.0, 4.0.1, and IE 9-10 also allow
                      // certain octal literals.
                      parseSupported = parse("01") !== 1;
                    });
                  }
                  if (parseSupported) {
                    attempt(function () {
                      // FF 4.0, 4.0.1, and Rhino 1.7R3-R4 allow trailing decimal
                      // points. These environments, along with FF 3.1b1 and 2,
                      // also allow trailing commas in JSON objects and arrays.
                      parseSupported = parse("1.") !== 1;
                    });
                  }
                }
              }
            }, function () {
              parseSupported = false;
            });
          }
          isSupported = parseSupported;
        }
      }
      return has[name] = !!isSupported;
    }
    has["bug-string-char-index"] = has["date-serialization"] = has["json"] = has["json-stringify"] = has["json-parse"] = null;

    if (!has("json")) {
      // Common `[[Class]]` name aliases.
      var functionClass = "[object Function]",
          dateClass = "[object Date]",
          numberClass = "[object Number]",
          stringClass = "[object String]",
          arrayClass = "[object Array]",
          booleanClass = "[object Boolean]";

      // Detect incomplete support for accessing string characters by index.
      var charIndexBuggy = has("bug-string-char-index");

      // Internal: Normalizes the `for...in` iteration algorithm across
      // environments. Each enumerated key is yielded to a `callback` function.
      var forOwn = function (object, callback) {
        var size = 0, Properties, dontEnums, property;

        // Tests for bugs in the current environment's `for...in` algorithm. The
        // `valueOf` property inherits the non-enumerable flag from
        // `Object.prototype` in older versions of IE, Netscape, and Mozilla.
        (Properties = function () {
          this.valueOf = 0;
        }).prototype.valueOf = 0;

        // Iterate over a new instance of the `Properties` class.
        dontEnums = new Properties();
        for (property in dontEnums) {
          // Ignore all properties inherited from `Object.prototype`.
          if (isProperty.call(dontEnums, property)) {
            size++;
          }
        }
        Properties = dontEnums = null;

        // Normalize the iteration algorithm.
        if (!size) {
          // A list of non-enumerable properties inherited from `Object.prototype`.
          dontEnums = ["valueOf", "toString", "toLocaleString", "propertyIsEnumerable", "isPrototypeOf", "hasOwnProperty", "constructor"];
          // IE <= 8, Mozilla 1.0, and Netscape 6.2 ignore shadowed non-enumerable
          // properties.
          forOwn = function (object, callback) {
            var isFunction = getClass.call(object) == functionClass, property, length;
            var hasProperty = !isFunction && typeof object.constructor != "function" && objectTypes[typeof object.hasOwnProperty] && object.hasOwnProperty || isProperty;
            for (property in object) {
              // Gecko <= 1.0 enumerates the `prototype` property of functions under
              // certain conditions; IE does not.
              if (!(isFunction && property == "prototype") && hasProperty.call(object, property)) {
                callback(property);
              }
            }
            // Manually invoke the callback for each non-enumerable property.
            for (length = dontEnums.length; property = dontEnums[--length];) {
              if (hasProperty.call(object, property)) {
                callback(property);
              }
            }
          };
        } else {
          // No bugs detected; use the standard `for...in` algorithm.
          forOwn = function (object, callback) {
            var isFunction = getClass.call(object) == functionClass, property, isConstructor;
            for (property in object) {
              if (!(isFunction && property == "prototype") && isProperty.call(object, property) && !(isConstructor = property === "constructor")) {
                callback(property);
              }
            }
            // Manually invoke the callback for the `constructor` property due to
            // cross-environment inconsistencies.
            if (isConstructor || isProperty.call(object, (property = "constructor"))) {
              callback(property);
            }
          };
        }
        return forOwn(object, callback);
      };

      // Public: Serializes a JavaScript `value` as a JSON string. The optional
      // `filter` argument may specify either a function that alters how object and
      // array members are serialized, or an array of strings and numbers that
      // indicates which properties should be serialized. The optional `width`
      // argument may be either a string or number that specifies the indentation
      // level of the output.
      if (!has("json-stringify") && !has("date-serialization")) {
        // Internal: A map of control characters and their escaped equivalents.
        var Escapes = {
          92: "\\\\",
          34: '\\"',
          8: "\\b",
          12: "\\f",
          10: "\\n",
          13: "\\r",
          9: "\\t"
        };

        // Internal: Converts `value` into a zero-padded string such that its
        // length is at least equal to `width`. The `width` must be <= 6.
        var leadingZeroes = "000000";
        var toPaddedString = function (width, value) {
          // The `|| 0` expression is necessary to work around a bug in
          // Opera <= 7.54u2 where `0 == -0`, but `String(-0) !== "0"`.
          return (leadingZeroes + (value || 0)).slice(-width);
        };

        // Internal: Serializes a date object.
        var serializeDate = function (value) {
          var getData, year, month, date, time, hours, minutes, seconds, milliseconds;
          // Define additional utility methods if the `Date` methods are buggy.
          if (!isExtended) {
            var floor = Math.floor;
            // A mapping between the months of the year and the number of days between
            // January 1st and the first of the respective month.
            var Months = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334];
            // Internal: Calculates the number of days between the Unix epoch and the
            // first day of the given month.
            var getDay = function (year, month) {
              return Months[month] + 365 * (year - 1970) + floor((year - 1969 + (month = +(month > 1))) / 4) - floor((year - 1901 + month) / 100) + floor((year - 1601 + month) / 400);
            };
            getData = function (value) {
              // Manually compute the year, month, date, hours, minutes,
              // seconds, and milliseconds if the `getUTC*` methods are
              // buggy. Adapted from @Yaffle's `date-shim` project.
              date = floor(value / 864e5);
              for (year = floor(date / 365.2425) + 1970 - 1; getDay(year + 1, 0) <= date; year++);
              for (month = floor((date - getDay(year, 0)) / 30.42); getDay(year, month + 1) <= date; month++);
              date = 1 + date - getDay(year, month);
              // The `time` value specifies the time within the day (see ES
              // 5.1 section 15.9.1.2). The formula `(A % B + B) % B` is used
              // to compute `A modulo B`, as the `%` operator does not
              // correspond to the `modulo` operation for negative numbers.
              time = (value % 864e5 + 864e5) % 864e5;
              // The hours, minutes, seconds, and milliseconds are obtained by
              // decomposing the time within the day. See section 15.9.1.10.
              hours = floor(time / 36e5) % 24;
              minutes = floor(time / 6e4) % 60;
              seconds = floor(time / 1e3) % 60;
              milliseconds = time % 1e3;
            };
          } else {
            getData = function (value) {
              year = value.getUTCFullYear();
              month = value.getUTCMonth();
              date = value.getUTCDate();
              hours = value.getUTCHours();
              minutes = value.getUTCMinutes();
              seconds = value.getUTCSeconds();
              milliseconds = value.getUTCMilliseconds();
            };
          }
          serializeDate = function (value) {
            if (value > -1 / 0 && value < 1 / 0) {
              // Dates are serialized according to the `Date#toJSON` method
              // specified in ES 5.1 section 15.9.5.44. See section 15.9.1.15
              // for the ISO 8601 date time string format.
              getData(value);
              // Serialize extended years correctly.
              value = (year <= 0 || year >= 1e4 ? (year < 0 ? "-" : "+") + toPaddedString(6, year < 0 ? -year : year) : toPaddedString(4, year)) +
              "-" + toPaddedString(2, month + 1) + "-" + toPaddedString(2, date) +
              // Months, dates, hours, minutes, and seconds should have two
              // digits; milliseconds should have three.
              "T" + toPaddedString(2, hours) + ":" + toPaddedString(2, minutes) + ":" + toPaddedString(2, seconds) +
              // Milliseconds are optional in ES 5.0, but required in 5.1.
              "." + toPaddedString(3, milliseconds) + "Z";
              year = month = date = hours = minutes = seconds = milliseconds = null;
            } else {
              value = null;
            }
            return value;
          };
          return serializeDate(value);
        };

        // For environments with `JSON.stringify` but buggy date serialization,
        // we override the native `Date#toJSON` implementation with a
        // spec-compliant one.
        if (has("json-stringify") && !has("date-serialization")) {
          // Internal: the `Date#toJSON` implementation used to override the native one.
          function dateToJSON (key) {
            return serializeDate(this);
          }

          // Public: `JSON.stringify`. See ES 5.1 section 15.12.3.
          var nativeStringify = exports.stringify;
          exports.stringify = function (source, filter, width) {
            var nativeToJSON = Date.prototype.toJSON;
            Date.prototype.toJSON = dateToJSON;
            var result = nativeStringify(source, filter, width);
            Date.prototype.toJSON = nativeToJSON;
            return result;
          }
        } else {
          // Internal: Double-quotes a string `value`, replacing all ASCII control
          // characters (characters with code unit values between 0 and 31) with
          // their escaped equivalents. This is an implementation of the
          // `Quote(value)` operation defined in ES 5.1 section 15.12.3.
          var unicodePrefix = "\\u00";
          var escapeChar = function (character) {
            var charCode = character.charCodeAt(0), escaped = Escapes[charCode];
            if (escaped) {
              return escaped;
            }
            return unicodePrefix + toPaddedString(2, charCode.toString(16));
          };
          var reEscape = /[\x00-\x1f\x22\x5c]/g;
          var quote = function (value) {
            reEscape.lastIndex = 0;
            return '"' +
              (
                reEscape.test(value)
                  ? value.replace(reEscape, escapeChar)
                  : value
              ) +
              '"';
          };

          // Internal: Recursively serializes an object. Implements the
          // `Str(key, holder)`, `JO(value)`, and `JA(value)` operations.
          var serialize = function (property, object, callback, properties, whitespace, indentation, stack) {
            var value, type, className, results, element, index, length, prefix, result;
            attempt(function () {
              // Necessary for host object support.
              value = object[property];
            });
            if (typeof value == "object" && value) {
              if (value.getUTCFullYear && getClass.call(value) == dateClass && value.toJSON === Date.prototype.toJSON) {
                value = serializeDate(value);
              } else if (typeof value.toJSON == "function") {
                value = value.toJSON(property);
              }
            }
            if (callback) {
              // If a replacement function was provided, call it to obtain the value
              // for serialization.
              value = callback.call(object, property, value);
            }
            // Exit early if value is `undefined` or `null`.
            if (value == undefined) {
              return value === undefined ? value : "null";
            }
            type = typeof value;
            // Only call `getClass` if the value is an object.
            if (type == "object") {
              className = getClass.call(value);
            }
            switch (className || type) {
              case "boolean":
              case booleanClass:
                // Booleans are represented literally.
                return "" + value;
              case "number":
              case numberClass:
                // JSON numbers must be finite. `Infinity` and `NaN` are serialized as
                // `"null"`.
                return value > -1 / 0 && value < 1 / 0 ? "" + value : "null";
              case "string":
              case stringClass:
                // Strings are double-quoted and escaped.
                return quote("" + value);
            }
            // Recursively serialize objects and arrays.
            if (typeof value == "object") {
              // Check for cyclic structures. This is a linear search; performance
              // is inversely proportional to the number of unique nested objects.
              for (length = stack.length; length--;) {
                if (stack[length] === value) {
                  // Cyclic structures cannot be serialized by `JSON.stringify`.
                  throw TypeError();
                }
              }
              // Add the object to the stack of traversed objects.
              stack.push(value);
              results = [];
              // Save the current indentation level and indent one additional level.
              prefix = indentation;
              indentation += whitespace;
              if (className == arrayClass) {
                // Recursively serialize array elements.
                for (index = 0, length = value.length; index < length; index++) {
                  element = serialize(index, value, callback, properties, whitespace, indentation, stack);
                  results.push(element === undefined ? "null" : element);
                }
                result = results.length ? (whitespace ? "[\n" + indentation + results.join(",\n" + indentation) + "\n" + prefix + "]" : ("[" + results.join(",") + "]")) : "[]";
              } else {
                // Recursively serialize object members. Members are selected from
                // either a user-specified list of property names, or the object
                // itself.
                forOwn(properties || value, function (property) {
                  var element = serialize(property, value, callback, properties, whitespace, indentation, stack);
                  if (element !== undefined) {
                    // According to ES 5.1 section 15.12.3: "If `gap` {whitespace}
                    // is not the empty string, let `member` {quote(property) + ":"}
                    // be the concatenation of `member` and the `space` character."
                    // The "`space` character" refers to the literal space
                    // character, not the `space` {width} argument provided to
                    // `JSON.stringify`.
                    results.push(quote(property) + ":" + (whitespace ? " " : "") + element);
                  }
                });
                result = results.length ? (whitespace ? "{\n" + indentation + results.join(",\n" + indentation) + "\n" + prefix + "}" : ("{" + results.join(",") + "}")) : "{}";
              }
              // Remove the object from the traversed object stack.
              stack.pop();
              return result;
            }
          };

          // Public: `JSON.stringify`. See ES 5.1 section 15.12.3.
          exports.stringify = function (source, filter, width) {
            var whitespace, callback, properties, className;
            if (objectTypes[typeof filter] && filter) {
              className = getClass.call(filter);
              if (className == functionClass) {
                callback = filter;
              } else if (className == arrayClass) {
                // Convert the property names array into a makeshift set.
                properties = {};
                for (var index = 0, length = filter.length, value; index < length;) {
                  value = filter[index++];
                  className = getClass.call(value);
                  if (className == "[object String]" || className == "[object Number]") {
                    properties[value] = 1;
                  }
                }
              }
            }
            if (width) {
              className = getClass.call(width);
              if (className == numberClass) {
                // Convert the `width` to an integer and create a string containing
                // `width` number of space characters.
                if ((width -= width % 1) > 0) {
                  if (width > 10) {
                    width = 10;
                  }
                  for (whitespace = ""; whitespace.length < width;) {
                    whitespace += " ";
                  }
                }
              } else if (className == stringClass) {
                whitespace = width.length <= 10 ? width : width.slice(0, 10);
              }
            }
            // Opera <= 7.54u2 discards the values associated with empty string keys
            // (`""`) only if they are used directly within an object member list
            // (e.g., `!("" in { "": 1})`).
            return serialize("", (value = {}, value[""] = source, value), callback, properties, whitespace, "", []);
          };
        }
      }

      // Public: Parses a JSON source string.
      if (!has("json-parse")) {
        var fromCharCode = String.fromCharCode;

        // Internal: A map of escaped control characters and their unescaped
        // equivalents.
        var Unescapes = {
          92: "\\",
          34: '"',
          47: "/",
          98: "\b",
          116: "\t",
          110: "\n",
          102: "\f",
          114: "\r"
        };

        // Internal: Stores the parser state.
        var Index, Source;

        // Internal: Resets the parser state and throws a `SyntaxError`.
        var abort = function () {
          Index = Source = null;
          throw SyntaxError();
        };

        // Internal: Returns the next token, or `"$"` if the parser has reached
        // the end of the source string. A token may be a string, number, `null`
        // literal, or Boolean literal.
        var lex = function () {
          var source = Source, length = source.length, value, begin, position, isSigned, charCode;
          while (Index < length) {
            charCode = source.charCodeAt(Index);
            switch (charCode) {
              case 9: case 10: case 13: case 32:
                // Skip whitespace tokens, including tabs, carriage returns, line
                // feeds, and space characters.
                Index++;
                break;
              case 123: case 125: case 91: case 93: case 58: case 44:
                // Parse a punctuator token (`{`, `}`, `[`, `]`, `:`, or `,`) at
                // the current position.
                value = charIndexBuggy ? source.charAt(Index) : source[Index];
                Index++;
                return value;
              case 34:
                // `"` delimits a JSON string; advance to the next character and
                // begin parsing the string. String tokens are prefixed with the
                // sentinel `@` character to distinguish them from punctuators and
                // end-of-string tokens.
                for (value = "@", Index++; Index < length;) {
                  charCode = source.charCodeAt(Index);
                  if (charCode < 32) {
                    // Unescaped ASCII control characters (those with a code unit
                    // less than the space character) are not permitted.
                    abort();
                  } else if (charCode == 92) {
                    // A reverse solidus (`\`) marks the beginning of an escaped
                    // control character (including `"`, `\`, and `/`) or Unicode
                    // escape sequence.
                    charCode = source.charCodeAt(++Index);
                    switch (charCode) {
                      case 92: case 34: case 47: case 98: case 116: case 110: case 102: case 114:
                        // Revive escaped control characters.
                        value += Unescapes[charCode];
                        Index++;
                        break;
                      case 117:
                        // `\u` marks the beginning of a Unicode escape sequence.
                        // Advance to the first character and validate the
                        // four-digit code point.
                        begin = ++Index;
                        for (position = Index + 4; Index < position; Index++) {
                          charCode = source.charCodeAt(Index);
                          // A valid sequence comprises four hexdigits (case-
                          // insensitive) that form a single hexadecimal value.
                          if (!(charCode >= 48 && charCode <= 57 || charCode >= 97 && charCode <= 102 || charCode >= 65 && charCode <= 70)) {
                            // Invalid Unicode escape sequence.
                            abort();
                          }
                        }
                        // Revive the escaped character.
                        value += fromCharCode("0x" + source.slice(begin, Index));
                        break;
                      default:
                        // Invalid escape sequence.
                        abort();
                    }
                  } else {
                    if (charCode == 34) {
                      // An unescaped double-quote character marks the end of the
                      // string.
                      break;
                    }
                    charCode = source.charCodeAt(Index);
                    begin = Index;
                    // Optimize for the common case where a string is valid.
                    while (charCode >= 32 && charCode != 92 && charCode != 34) {
                      charCode = source.charCodeAt(++Index);
                    }
                    // Append the string as-is.
                    value += source.slice(begin, Index);
                  }
                }
                if (source.charCodeAt(Index) == 34) {
                  // Advance to the next character and return the revived string.
                  Index++;
                  return value;
                }
                // Unterminated string.
                abort();
              default:
                // Parse numbers and literals.
                begin = Index;
                // Advance past the negative sign, if one is specified.
                if (charCode == 45) {
                  isSigned = true;
                  charCode = source.charCodeAt(++Index);
                }
                // Parse an integer or floating-point value.
                if (charCode >= 48 && charCode <= 57) {
                  // Leading zeroes are interpreted as octal literals.
                  if (charCode == 48 && ((charCode = source.charCodeAt(Index + 1)), charCode >= 48 && charCode <= 57)) {
                    // Illegal octal literal.
                    abort();
                  }
                  isSigned = false;
                  // Parse the integer component.
                  for (; Index < length && ((charCode = source.charCodeAt(Index)), charCode >= 48 && charCode <= 57); Index++);
                  // Floats cannot contain a leading decimal point; however, this
                  // case is already accounted for by the parser.
                  if (source.charCodeAt(Index) == 46) {
                    position = ++Index;
                    // Parse the decimal component.
                    for (; position < length; position++) {
                      charCode = source.charCodeAt(position);
                      if (charCode < 48 || charCode > 57) {
                        break;
                      }
                    }
                    if (position == Index) {
                      // Illegal trailing decimal.
                      abort();
                    }
                    Index = position;
                  }
                  // Parse exponents. The `e` denoting the exponent is
                  // case-insensitive.
                  charCode = source.charCodeAt(Index);
                  if (charCode == 101 || charCode == 69) {
                    charCode = source.charCodeAt(++Index);
                    // Skip past the sign following the exponent, if one is
                    // specified.
                    if (charCode == 43 || charCode == 45) {
                      Index++;
                    }
                    // Parse the exponential component.
                    for (position = Index; position < length; position++) {
                      charCode = source.charCodeAt(position);
                      if (charCode < 48 || charCode > 57) {
                        break;
                      }
                    }
                    if (position == Index) {
                      // Illegal empty exponent.
                      abort();
                    }
                    Index = position;
                  }
                  // Coerce the parsed value to a JavaScript number.
                  return +source.slice(begin, Index);
                }
                // A negative sign may only precede numbers.
                if (isSigned) {
                  abort();
                }
                // `true`, `false`, and `null` literals.
                var temp = source.slice(Index, Index + 4);
                if (temp == "true") {
                  Index += 4;
                  return true;
                } else if (temp == "fals" && source.charCodeAt(Index + 4 ) == 101) {
                  Index += 5;
                  return false;
                } else if (temp == "null") {
                  Index += 4;
                  return null;
                }
                // Unrecognized token.
                abort();
            }
          }
          // Return the sentinel `$` character if the parser has reached the end
          // of the source string.
          return "$";
        };

        // Internal: Parses a JSON `value` token.
        var get = function (value) {
          var results, hasMembers;
          if (value == "$") {
            // Unexpected end of input.
            abort();
          }
          if (typeof value == "string") {
            if ((charIndexBuggy ? value.charAt(0) : value[0]) == "@") {
              // Remove the sentinel `@` character.
              return value.slice(1);
            }
            // Parse object and array literals.
            if (value == "[") {
              // Parses a JSON array, returning a new JavaScript array.
              results = [];
              for (;;) {
                value = lex();
                // A closing square bracket marks the end of the array literal.
                if (value == "]") {
                  break;
                }
                // If the array literal contains elements, the current token
                // should be a comma separating the previous element from the
                // next.
                if (hasMembers) {
                  if (value == ",") {
                    value = lex();
                    if (value == "]") {
                      // Unexpected trailing `,` in array literal.
                      abort();
                    }
                  } else {
                    // A `,` must separate each array element.
                    abort();
                  }
                } else {
                  hasMembers = true;
                }
                // Elisions and leading commas are not permitted.
                if (value == ",") {
                  abort();
                }
                results.push(get(value));
              }
              return results;
            } else if (value == "{") {
              // Parses a JSON object, returning a new JavaScript object.
              results = {};
              for (;;) {
                value = lex();
                // A closing curly brace marks the end of the object literal.
                if (value == "}") {
                  break;
                }
                // If the object literal contains members, the current token
                // should be a comma separator.
                if (hasMembers) {
                  if (value == ",") {
                    value = lex();
                    if (value == "}") {
                      // Unexpected trailing `,` in object literal.
                      abort();
                    }
                  } else {
                    // A `,` must separate each object member.
                    abort();
                  }
                } else {
                  hasMembers = true;
                }
                // Leading commas are not permitted, object property names must be
                // double-quoted strings, and a `:` must separate each property
                // name and value.
                if (value == "," || typeof value != "string" || (charIndexBuggy ? value.charAt(0) : value[0]) != "@" || lex() != ":") {
                  abort();
                }
                results[value.slice(1)] = get(lex());
              }
              return results;
            }
            // Unexpected token encountered.
            abort();
          }
          return value;
        };

        // Internal: Updates a traversed object member.
        var update = function (source, property, callback) {
          var element = walk(source, property, callback);
          if (element === undefined) {
            delete source[property];
          } else {
            source[property] = element;
          }
        };

        // Internal: Recursively traverses a parsed JSON object, invoking the
        // `callback` function for each value. This is an implementation of the
        // `Walk(holder, name)` operation defined in ES 5.1 section 15.12.2.
        var walk = function (source, property, callback) {
          var value = source[property], length;
          if (typeof value == "object" && value) {
            // `forOwn` can't be used to traverse an array in Opera <= 8.54
            // because its `Object#hasOwnProperty` implementation returns `false`
            // for array indices (e.g., `![1, 2, 3].hasOwnProperty("0")`).
            if (getClass.call(value) == arrayClass) {
              for (length = value.length; length--;) {
                update(getClass, forOwn, value, length, callback);
              }
            } else {
              forOwn(value, function (property) {
                update(value, property, callback);
              });
            }
          }
          return callback.call(source, property, value);
        };

        // Public: `JSON.parse`. See ES 5.1 section 15.12.2.
        exports.parse = function (source, callback) {
          var result, value;
          Index = 0;
          Source = "" + source;
          result = get(lex());
          // If a JSON string contains multiple tokens, it is invalid.
          if (lex() != "$") {
            abort();
          }
          // Reset the parser state.
          Index = Source = null;
          return callback && getClass.call(callback) == functionClass ? walk((value = {}, value[""] = result, value), "", callback) : result;
        };
      }
    }

    exports.runInContext = runInContext;
    return exports;
  }

  if (freeExports && !isLoader) {
    // Export for CommonJS environments.
    runInContext(root, freeExports);
  } else {
    // Export for web browsers and JavaScript engines.
    var nativeJSON = root.JSON,
        previousJSON = root.JSON3,
        isRestored = false;

    var JSON3 = runInContext(root, (root.JSON3 = {
      // Public: Restores the original value of the global `JSON` object and
      // returns a reference to the `JSON3` object.
      "noConflict": function () {
        if (!isRestored) {
          isRestored = true;
          root.JSON = nativeJSON;
          root.JSON3 = previousJSON;
          nativeJSON = previousJSON = null;
        }
        return JSON3;
      }
    }));

    root.JSON = {
      "parse": JSON3.parse,
      "stringify": JSON3.stringify
    };
  }

  // Export for asynchronous module loaders.
  if (isLoader) {
    define(function () {
      return JSON3;
    });
  }
}).call(this);

}).call(this)}).call(this,typeof global !== "undefined" ? global : typeof self !== "undefined" ? self : typeof window !== "undefined" ? window : {})
},{}],68:[function(require,module,exports){
/**
 * Module dependencies
 */

var debug = require('debug')('jsonp');

/**
 * Module exports.
 */

module.exports = jsonp;

/**
 * Callback index.
 */

var count = 0;

/**
 * Noop function.
 */

function noop(){}

/**
 * JSONP handler
 *
 * Options:
 *  - param {String} qs parameter (`callback`)
 *  - prefix {String} qs parameter (`__jp`)
 *  - name {String} qs parameter (`prefix` + incr)
 *  - timeout {Number} how long after a timeout error is emitted (`60000`)
 *
 * @param {String} url
 * @param {Object|Function} optional options / callback
 * @param {Function} optional callback
 */

function jsonp(url, opts, fn){
  if ('function' == typeof opts) {
    fn = opts;
    opts = {};
  }
  if (!opts) opts = {};

  var prefix = opts.prefix || '__jp';

  // use the callback name that was passed if one was provided.
  // otherwise generate a unique name by incrementing our counter.
  var id = opts.name || (prefix + (count++));

  var param = opts.param || 'callback';
  var timeout = null != opts.timeout ? opts.timeout : 60000;
  var enc = encodeURIComponent;
  var target = document.getElementsByTagName('script')[0] || document.head;
  var script;
  var timer;


  if (timeout) {
    timer = setTimeout(function(){
      cleanup();
      if (fn) fn(new Error('Timeout'));
    }, timeout);
  }

  function cleanup(){
    if (script.parentNode) script.parentNode.removeChild(script);
    window[id] = noop;
    if (timer) clearTimeout(timer);
  }

  function cancel(){
    if (window[id]) {
      cleanup();
    }
  }

  window[id] = function(data){
    debug('jsonp got', data);
    cleanup();
    if (fn) fn(null, data);
  };

  // add qs component
  url += (~url.indexOf('?') ? '&' : '?') + param + '=' + enc(id);
  url = url.replace('?&', '?');

  debug('jsonp req "%s"', url);

  // create script
  script = document.createElement('script');
  script.src = url;
  target.parentNode.insertBefore(script, target);

  return cancel;
}

},{"debug":59}],69:[function(require,module,exports){
/**
 * Module dependencies.
 */

var is = require('is');
var onload = require('script-onload');
var tick = require('next-tick');

/**
 * Expose `loadScript`.
 *
 * @param {Object} options
 * @param {Function} fn
 * @api public
 */

module.exports = function loadIframe(options, fn){
  if (!options) throw new Error('Cant load nothing...');

  // Allow for the simplest case, just passing a `src` string.
  if (is.string(options)) options = { src : options };

  var https = document.location.protocol === 'https:' ||
              document.location.protocol === 'chrome-extension:';

  // If you use protocol relative URLs, third-party scripts like Google
  // Analytics break when testing with `file:` so this fixes that.
  if (options.src && options.src.indexOf('//') === 0) {
    options.src = https ? 'https:' + options.src : 'http:' + options.src;
  }

  // Allow them to pass in different URLs depending on the protocol.
  if (https && options.https) options.src = options.https;
  else if (!https && options.http) options.src = options.http;

  // Make the `<iframe>` element and insert it before the first iframe on the
  // page, which is guaranteed to exist since this Javaiframe is running.
  var iframe = document.createElement('iframe');
  iframe.src = options.src;
  iframe.width = options.width || 1;
  iframe.height = options.height || 1;
  iframe.style.display = 'none';

  // If we have a fn, attach event handlers, even in IE. Based off of
  // the Third-Party Javascript script loading example:
  // https://github.com/thirdpartyjs/thirdpartyjs-code/blob/master/examples/templates/02/loading-files/index.html
  if (is.fn(fn)) {
    onload(iframe, fn);
  }

  tick(function(){
    // Append after event listeners are attached for IE.
    var firstScript = document.getElementsByTagName('script')[0];
    firstScript.parentNode.insertBefore(iframe, firstScript);
  });

  // Return the iframe element in case they want to do anything special, like
  // give it an ID or attributes.
  return iframe;
};

},{"is":66,"next-tick":76,"script-onload":79}],70:[function(require,module,exports){
(function (global){(function (){
/**
 * lodash (Custom Build) <https://lodash.com/>
 * Build: `lodash modularize exports="npm" -o ./`
 * Copyright jQuery Foundation and other contributors <https://jquery.org/>
 * Released under MIT license <https://lodash.com/license>
 * Based on Underscore.js 1.8.3 <http://underscorejs.org/LICENSE>
 * Copyright Jeremy Ashkenas, DocumentCloud and Investigative Reporters & Editors
 */

/** Used as the size to enable large array optimizations. */
var LARGE_ARRAY_SIZE = 200;

/** Used to stand-in for `undefined` hash values. */
var HASH_UNDEFINED = '__lodash_hash_undefined__';

/** Used as references for various `Number` constants. */
var MAX_SAFE_INTEGER = 9007199254740991;

/** `Object#toString` result references. */
var argsTag = '[object Arguments]',
    arrayTag = '[object Array]',
    boolTag = '[object Boolean]',
    dateTag = '[object Date]',
    errorTag = '[object Error]',
    funcTag = '[object Function]',
    genTag = '[object GeneratorFunction]',
    mapTag = '[object Map]',
    numberTag = '[object Number]',
    objectTag = '[object Object]',
    promiseTag = '[object Promise]',
    regexpTag = '[object RegExp]',
    setTag = '[object Set]',
    stringTag = '[object String]',
    symbolTag = '[object Symbol]',
    weakMapTag = '[object WeakMap]';

var arrayBufferTag = '[object ArrayBuffer]',
    dataViewTag = '[object DataView]',
    float32Tag = '[object Float32Array]',
    float64Tag = '[object Float64Array]',
    int8Tag = '[object Int8Array]',
    int16Tag = '[object Int16Array]',
    int32Tag = '[object Int32Array]',
    uint8Tag = '[object Uint8Array]',
    uint8ClampedTag = '[object Uint8ClampedArray]',
    uint16Tag = '[object Uint16Array]',
    uint32Tag = '[object Uint32Array]';

/**
 * Used to match `RegExp`
 * [syntax characters](http://ecma-international.org/ecma-262/7.0/#sec-patterns).
 */
var reRegExpChar = /[\\^$.*+?()[\]{}|]/g;

/** Used to match `RegExp` flags from their coerced string values. */
var reFlags = /\w*$/;

/** Used to detect host constructors (Safari). */
var reIsHostCtor = /^\[object .+?Constructor\]$/;

/** Used to detect unsigned integer values. */
var reIsUint = /^(?:0|[1-9]\d*)$/;

/** Used to identify `toStringTag` values supported by `_.clone`. */
var cloneableTags = {};
cloneableTags[argsTag] = cloneableTags[arrayTag] =
cloneableTags[arrayBufferTag] = cloneableTags[dataViewTag] =
cloneableTags[boolTag] = cloneableTags[dateTag] =
cloneableTags[float32Tag] = cloneableTags[float64Tag] =
cloneableTags[int8Tag] = cloneableTags[int16Tag] =
cloneableTags[int32Tag] = cloneableTags[mapTag] =
cloneableTags[numberTag] = cloneableTags[objectTag] =
cloneableTags[regexpTag] = cloneableTags[setTag] =
cloneableTags[stringTag] = cloneableTags[symbolTag] =
cloneableTags[uint8Tag] = cloneableTags[uint8ClampedTag] =
cloneableTags[uint16Tag] = cloneableTags[uint32Tag] = true;
cloneableTags[errorTag] = cloneableTags[funcTag] =
cloneableTags[weakMapTag] = false;

/** Detect free variable `global` from Node.js. */
var freeGlobal = typeof global == 'object' && global && global.Object === Object && global;

/** Detect free variable `self`. */
var freeSelf = typeof self == 'object' && self && self.Object === Object && self;

/** Used as a reference to the global object. */
var root = freeGlobal || freeSelf || Function('return this')();

/** Detect free variable `exports`. */
var freeExports = typeof exports == 'object' && exports && !exports.nodeType && exports;

/** Detect free variable `module`. */
var freeModule = freeExports && typeof module == 'object' && module && !module.nodeType && module;

/** Detect the popular CommonJS extension `module.exports`. */
var moduleExports = freeModule && freeModule.exports === freeExports;

/**
 * Adds the key-value `pair` to `map`.
 *
 * @private
 * @param {Object} map The map to modify.
 * @param {Array} pair The key-value pair to add.
 * @returns {Object} Returns `map`.
 */
function addMapEntry(map, pair) {
  // Don't return `map.set` because it's not chainable in IE 11.
  map.set(pair[0], pair[1]);
  return map;
}

/**
 * Adds `value` to `set`.
 *
 * @private
 * @param {Object} set The set to modify.
 * @param {*} value The value to add.
 * @returns {Object} Returns `set`.
 */
function addSetEntry(set, value) {
  // Don't return `set.add` because it's not chainable in IE 11.
  set.add(value);
  return set;
}

/**
 * A specialized version of `_.forEach` for arrays without support for
 * iteratee shorthands.
 *
 * @private
 * @param {Array} [array] The array to iterate over.
 * @param {Function} iteratee The function invoked per iteration.
 * @returns {Array} Returns `array`.
 */
function arrayEach(array, iteratee) {
  var index = -1,
      length = array ? array.length : 0;

  while (++index < length) {
    if (iteratee(array[index], index, array) === false) {
      break;
    }
  }
  return array;
}

/**
 * Appends the elements of `values` to `array`.
 *
 * @private
 * @param {Array} array The array to modify.
 * @param {Array} values The values to append.
 * @returns {Array} Returns `array`.
 */
function arrayPush(array, values) {
  var index = -1,
      length = values.length,
      offset = array.length;

  while (++index < length) {
    array[offset + index] = values[index];
  }
  return array;
}

/**
 * A specialized version of `_.reduce` for arrays without support for
 * iteratee shorthands.
 *
 * @private
 * @param {Array} [array] The array to iterate over.
 * @param {Function} iteratee The function invoked per iteration.
 * @param {*} [accumulator] The initial value.
 * @param {boolean} [initAccum] Specify using the first element of `array` as
 *  the initial value.
 * @returns {*} Returns the accumulated value.
 */
function arrayReduce(array, iteratee, accumulator, initAccum) {
  var index = -1,
      length = array ? array.length : 0;

  if (initAccum && length) {
    accumulator = array[++index];
  }
  while (++index < length) {
    accumulator = iteratee(accumulator, array[index], index, array);
  }
  return accumulator;
}

/**
 * The base implementation of `_.times` without support for iteratee shorthands
 * or max array length checks.
 *
 * @private
 * @param {number} n The number of times to invoke `iteratee`.
 * @param {Function} iteratee The function invoked per iteration.
 * @returns {Array} Returns the array of results.
 */
function baseTimes(n, iteratee) {
  var index = -1,
      result = Array(n);

  while (++index < n) {
    result[index] = iteratee(index);
  }
  return result;
}

/**
 * Gets the value at `key` of `object`.
 *
 * @private
 * @param {Object} [object] The object to query.
 * @param {string} key The key of the property to get.
 * @returns {*} Returns the property value.
 */
function getValue(object, key) {
  return object == null ? undefined : object[key];
}

/**
 * Checks if `value` is a host object in IE < 9.
 *
 * @private
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is a host object, else `false`.
 */
function isHostObject(value) {
  // Many host objects are `Object` objects that can coerce to strings
  // despite having improperly defined `toString` methods.
  var result = false;
  if (value != null && typeof value.toString != 'function') {
    try {
      result = !!(value + '');
    } catch (e) {}
  }
  return result;
}

/**
 * Converts `map` to its key-value pairs.
 *
 * @private
 * @param {Object} map The map to convert.
 * @returns {Array} Returns the key-value pairs.
 */
function mapToArray(map) {
  var index = -1,
      result = Array(map.size);

  map.forEach(function(value, key) {
    result[++index] = [key, value];
  });
  return result;
}

/**
 * Creates a unary function that invokes `func` with its argument transformed.
 *
 * @private
 * @param {Function} func The function to wrap.
 * @param {Function} transform The argument transform.
 * @returns {Function} Returns the new function.
 */
function overArg(func, transform) {
  return function(arg) {
    return func(transform(arg));
  };
}

/**
 * Converts `set` to an array of its values.
 *
 * @private
 * @param {Object} set The set to convert.
 * @returns {Array} Returns the values.
 */
function setToArray(set) {
  var index = -1,
      result = Array(set.size);

  set.forEach(function(value) {
    result[++index] = value;
  });
  return result;
}

/** Used for built-in method references. */
var arrayProto = Array.prototype,
    funcProto = Function.prototype,
    objectProto = Object.prototype;

/** Used to detect overreaching core-js shims. */
var coreJsData = root['__core-js_shared__'];

/** Used to detect methods masquerading as native. */
var maskSrcKey = (function() {
  var uid = /[^.]+$/.exec(coreJsData && coreJsData.keys && coreJsData.keys.IE_PROTO || '');
  return uid ? ('Symbol(src)_1.' + uid) : '';
}());

/** Used to resolve the decompiled source of functions. */
var funcToString = funcProto.toString;

/** Used to check objects for own properties. */
var hasOwnProperty = objectProto.hasOwnProperty;

/**
 * Used to resolve the
 * [`toStringTag`](http://ecma-international.org/ecma-262/7.0/#sec-object.prototype.tostring)
 * of values.
 */
var objectToString = objectProto.toString;

/** Used to detect if a method is native. */
var reIsNative = RegExp('^' +
  funcToString.call(hasOwnProperty).replace(reRegExpChar, '\\$&')
  .replace(/hasOwnProperty|(function).*?(?=\\\()| for .+?(?=\\\])/g, '$1.*?') + '$'
);

/** Built-in value references. */
var Buffer = moduleExports ? root.Buffer : undefined,
    Symbol = root.Symbol,
    Uint8Array = root.Uint8Array,
    getPrototype = overArg(Object.getPrototypeOf, Object),
    objectCreate = Object.create,
    propertyIsEnumerable = objectProto.propertyIsEnumerable,
    splice = arrayProto.splice;

/* Built-in method references for those with the same name as other `lodash` methods. */
var nativeGetSymbols = Object.getOwnPropertySymbols,
    nativeIsBuffer = Buffer ? Buffer.isBuffer : undefined,
    nativeKeys = overArg(Object.keys, Object);

/* Built-in method references that are verified to be native. */
var DataView = getNative(root, 'DataView'),
    Map = getNative(root, 'Map'),
    Promise = getNative(root, 'Promise'),
    Set = getNative(root, 'Set'),
    WeakMap = getNative(root, 'WeakMap'),
    nativeCreate = getNative(Object, 'create');

/** Used to detect maps, sets, and weakmaps. */
var dataViewCtorString = toSource(DataView),
    mapCtorString = toSource(Map),
    promiseCtorString = toSource(Promise),
    setCtorString = toSource(Set),
    weakMapCtorString = toSource(WeakMap);

/** Used to convert symbols to primitives and strings. */
var symbolProto = Symbol ? Symbol.prototype : undefined,
    symbolValueOf = symbolProto ? symbolProto.valueOf : undefined;

/**
 * Creates a hash object.
 *
 * @private
 * @constructor
 * @param {Array} [entries] The key-value pairs to cache.
 */
function Hash(entries) {
  var index = -1,
      length = entries ? entries.length : 0;

  this.clear();
  while (++index < length) {
    var entry = entries[index];
    this.set(entry[0], entry[1]);
  }
}

/**
 * Removes all key-value entries from the hash.
 *
 * @private
 * @name clear
 * @memberOf Hash
 */
function hashClear() {
  this.__data__ = nativeCreate ? nativeCreate(null) : {};
}

/**
 * Removes `key` and its value from the hash.
 *
 * @private
 * @name delete
 * @memberOf Hash
 * @param {Object} hash The hash to modify.
 * @param {string} key The key of the value to remove.
 * @returns {boolean} Returns `true` if the entry was removed, else `false`.
 */
function hashDelete(key) {
  return this.has(key) && delete this.__data__[key];
}

/**
 * Gets the hash value for `key`.
 *
 * @private
 * @name get
 * @memberOf Hash
 * @param {string} key The key of the value to get.
 * @returns {*} Returns the entry value.
 */
function hashGet(key) {
  var data = this.__data__;
  if (nativeCreate) {
    var result = data[key];
    return result === HASH_UNDEFINED ? undefined : result;
  }
  return hasOwnProperty.call(data, key) ? data[key] : undefined;
}

/**
 * Checks if a hash value for `key` exists.
 *
 * @private
 * @name has
 * @memberOf Hash
 * @param {string} key The key of the entry to check.
 * @returns {boolean} Returns `true` if an entry for `key` exists, else `false`.
 */
function hashHas(key) {
  var data = this.__data__;
  return nativeCreate ? data[key] !== undefined : hasOwnProperty.call(data, key);
}

/**
 * Sets the hash `key` to `value`.
 *
 * @private
 * @name set
 * @memberOf Hash
 * @param {string} key The key of the value to set.
 * @param {*} value The value to set.
 * @returns {Object} Returns the hash instance.
 */
function hashSet(key, value) {
  var data = this.__data__;
  data[key] = (nativeCreate && value === undefined) ? HASH_UNDEFINED : value;
  return this;
}

// Add methods to `Hash`.
Hash.prototype.clear = hashClear;
Hash.prototype['delete'] = hashDelete;
Hash.prototype.get = hashGet;
Hash.prototype.has = hashHas;
Hash.prototype.set = hashSet;

/**
 * Creates an list cache object.
 *
 * @private
 * @constructor
 * @param {Array} [entries] The key-value pairs to cache.
 */
function ListCache(entries) {
  var index = -1,
      length = entries ? entries.length : 0;

  this.clear();
  while (++index < length) {
    var entry = entries[index];
    this.set(entry[0], entry[1]);
  }
}

/**
 * Removes all key-value entries from the list cache.
 *
 * @private
 * @name clear
 * @memberOf ListCache
 */
function listCacheClear() {
  this.__data__ = [];
}

/**
 * Removes `key` and its value from the list cache.
 *
 * @private
 * @name delete
 * @memberOf ListCache
 * @param {string} key The key of the value to remove.
 * @returns {boolean} Returns `true` if the entry was removed, else `false`.
 */
function listCacheDelete(key) {
  var data = this.__data__,
      index = assocIndexOf(data, key);

  if (index < 0) {
    return false;
  }
  var lastIndex = data.length - 1;
  if (index == lastIndex) {
    data.pop();
  } else {
    splice.call(data, index, 1);
  }
  return true;
}

/**
 * Gets the list cache value for `key`.
 *
 * @private
 * @name get
 * @memberOf ListCache
 * @param {string} key The key of the value to get.
 * @returns {*} Returns the entry value.
 */
function listCacheGet(key) {
  var data = this.__data__,
      index = assocIndexOf(data, key);

  return index < 0 ? undefined : data[index][1];
}

/**
 * Checks if a list cache value for `key` exists.
 *
 * @private
 * @name has
 * @memberOf ListCache
 * @param {string} key The key of the entry to check.
 * @returns {boolean} Returns `true` if an entry for `key` exists, else `false`.
 */
function listCacheHas(key) {
  return assocIndexOf(this.__data__, key) > -1;
}

/**
 * Sets the list cache `key` to `value`.
 *
 * @private
 * @name set
 * @memberOf ListCache
 * @param {string} key The key of the value to set.
 * @param {*} value The value to set.
 * @returns {Object} Returns the list cache instance.
 */
function listCacheSet(key, value) {
  var data = this.__data__,
      index = assocIndexOf(data, key);

  if (index < 0) {
    data.push([key, value]);
  } else {
    data[index][1] = value;
  }
  return this;
}

// Add methods to `ListCache`.
ListCache.prototype.clear = listCacheClear;
ListCache.prototype['delete'] = listCacheDelete;
ListCache.prototype.get = listCacheGet;
ListCache.prototype.has = listCacheHas;
ListCache.prototype.set = listCacheSet;

/**
 * Creates a map cache object to store key-value pairs.
 *
 * @private
 * @constructor
 * @param {Array} [entries] The key-value pairs to cache.
 */
function MapCache(entries) {
  var index = -1,
      length = entries ? entries.length : 0;

  this.clear();
  while (++index < length) {
    var entry = entries[index];
    this.set(entry[0], entry[1]);
  }
}

/**
 * Removes all key-value entries from the map.
 *
 * @private
 * @name clear
 * @memberOf MapCache
 */
function mapCacheClear() {
  this.__data__ = {
    'hash': new Hash,
    'map': new (Map || ListCache),
    'string': new Hash
  };
}

/**
 * Removes `key` and its value from the map.
 *
 * @private
 * @name delete
 * @memberOf MapCache
 * @param {string} key The key of the value to remove.
 * @returns {boolean} Returns `true` if the entry was removed, else `false`.
 */
function mapCacheDelete(key) {
  return getMapData(this, key)['delete'](key);
}

/**
 * Gets the map value for `key`.
 *
 * @private
 * @name get
 * @memberOf MapCache
 * @param {string} key The key of the value to get.
 * @returns {*} Returns the entry value.
 */
function mapCacheGet(key) {
  return getMapData(this, key).get(key);
}

/**
 * Checks if a map value for `key` exists.
 *
 * @private
 * @name has
 * @memberOf MapCache
 * @param {string} key The key of the entry to check.
 * @returns {boolean} Returns `true` if an entry for `key` exists, else `false`.
 */
function mapCacheHas(key) {
  return getMapData(this, key).has(key);
}

/**
 * Sets the map `key` to `value`.
 *
 * @private
 * @name set
 * @memberOf MapCache
 * @param {string} key The key of the value to set.
 * @param {*} value The value to set.
 * @returns {Object} Returns the map cache instance.
 */
function mapCacheSet(key, value) {
  getMapData(this, key).set(key, value);
  return this;
}

// Add methods to `MapCache`.
MapCache.prototype.clear = mapCacheClear;
MapCache.prototype['delete'] = mapCacheDelete;
MapCache.prototype.get = mapCacheGet;
MapCache.prototype.has = mapCacheHas;
MapCache.prototype.set = mapCacheSet;

/**
 * Creates a stack cache object to store key-value pairs.
 *
 * @private
 * @constructor
 * @param {Array} [entries] The key-value pairs to cache.
 */
function Stack(entries) {
  this.__data__ = new ListCache(entries);
}

/**
 * Removes all key-value entries from the stack.
 *
 * @private
 * @name clear
 * @memberOf Stack
 */
function stackClear() {
  this.__data__ = new ListCache;
}

/**
 * Removes `key` and its value from the stack.
 *
 * @private
 * @name delete
 * @memberOf Stack
 * @param {string} key The key of the value to remove.
 * @returns {boolean} Returns `true` if the entry was removed, else `false`.
 */
function stackDelete(key) {
  return this.__data__['delete'](key);
}

/**
 * Gets the stack value for `key`.
 *
 * @private
 * @name get
 * @memberOf Stack
 * @param {string} key The key of the value to get.
 * @returns {*} Returns the entry value.
 */
function stackGet(key) {
  return this.__data__.get(key);
}

/**
 * Checks if a stack value for `key` exists.
 *
 * @private
 * @name has
 * @memberOf Stack
 * @param {string} key The key of the entry to check.
 * @returns {boolean} Returns `true` if an entry for `key` exists, else `false`.
 */
function stackHas(key) {
  return this.__data__.has(key);
}

/**
 * Sets the stack `key` to `value`.
 *
 * @private
 * @name set
 * @memberOf Stack
 * @param {string} key The key of the value to set.
 * @param {*} value The value to set.
 * @returns {Object} Returns the stack cache instance.
 */
function stackSet(key, value) {
  var cache = this.__data__;
  if (cache instanceof ListCache) {
    var pairs = cache.__data__;
    if (!Map || (pairs.length < LARGE_ARRAY_SIZE - 1)) {
      pairs.push([key, value]);
      return this;
    }
    cache = this.__data__ = new MapCache(pairs);
  }
  cache.set(key, value);
  return this;
}

// Add methods to `Stack`.
Stack.prototype.clear = stackClear;
Stack.prototype['delete'] = stackDelete;
Stack.prototype.get = stackGet;
Stack.prototype.has = stackHas;
Stack.prototype.set = stackSet;

/**
 * Creates an array of the enumerable property names of the array-like `value`.
 *
 * @private
 * @param {*} value The value to query.
 * @param {boolean} inherited Specify returning inherited property names.
 * @returns {Array} Returns the array of property names.
 */
function arrayLikeKeys(value, inherited) {
  // Safari 8.1 makes `arguments.callee` enumerable in strict mode.
  // Safari 9 makes `arguments.length` enumerable in strict mode.
  var result = (isArray(value) || isArguments(value))
    ? baseTimes(value.length, String)
    : [];

  var length = result.length,
      skipIndexes = !!length;

  for (var key in value) {
    if ((inherited || hasOwnProperty.call(value, key)) &&
        !(skipIndexes && (key == 'length' || isIndex(key, length)))) {
      result.push(key);
    }
  }
  return result;
}

/**
 * Assigns `value` to `key` of `object` if the existing value is not equivalent
 * using [`SameValueZero`](http://ecma-international.org/ecma-262/7.0/#sec-samevaluezero)
 * for equality comparisons.
 *
 * @private
 * @param {Object} object The object to modify.
 * @param {string} key The key of the property to assign.
 * @param {*} value The value to assign.
 */
function assignValue(object, key, value) {
  var objValue = object[key];
  if (!(hasOwnProperty.call(object, key) && eq(objValue, value)) ||
      (value === undefined && !(key in object))) {
    object[key] = value;
  }
}

/**
 * Gets the index at which the `key` is found in `array` of key-value pairs.
 *
 * @private
 * @param {Array} array The array to inspect.
 * @param {*} key The key to search for.
 * @returns {number} Returns the index of the matched value, else `-1`.
 */
function assocIndexOf(array, key) {
  var length = array.length;
  while (length--) {
    if (eq(array[length][0], key)) {
      return length;
    }
  }
  return -1;
}

/**
 * The base implementation of `_.assign` without support for multiple sources
 * or `customizer` functions.
 *
 * @private
 * @param {Object} object The destination object.
 * @param {Object} source The source object.
 * @returns {Object} Returns `object`.
 */
function baseAssign(object, source) {
  return object && copyObject(source, keys(source), object);
}

/**
 * The base implementation of `_.clone` and `_.cloneDeep` which tracks
 * traversed objects.
 *
 * @private
 * @param {*} value The value to clone.
 * @param {boolean} [isDeep] Specify a deep clone.
 * @param {boolean} [isFull] Specify a clone including symbols.
 * @param {Function} [customizer] The function to customize cloning.
 * @param {string} [key] The key of `value`.
 * @param {Object} [object] The parent object of `value`.
 * @param {Object} [stack] Tracks traversed objects and their clone counterparts.
 * @returns {*} Returns the cloned value.
 */
function baseClone(value, isDeep, isFull, customizer, key, object, stack) {
  var result;
  if (customizer) {
    result = object ? customizer(value, key, object, stack) : customizer(value);
  }
  if (result !== undefined) {
    return result;
  }
  if (!isObject(value)) {
    return value;
  }
  var isArr = isArray(value);
  if (isArr) {
    result = initCloneArray(value);
    if (!isDeep) {
      return copyArray(value, result);
    }
  } else {
    var tag = getTag(value),
        isFunc = tag == funcTag || tag == genTag;

    if (isBuffer(value)) {
      return cloneBuffer(value, isDeep);
    }
    if (tag == objectTag || tag == argsTag || (isFunc && !object)) {
      if (isHostObject(value)) {
        return object ? value : {};
      }
      result = initCloneObject(isFunc ? {} : value);
      if (!isDeep) {
        return copySymbols(value, baseAssign(result, value));
      }
    } else {
      if (!cloneableTags[tag]) {
        return object ? value : {};
      }
      result = initCloneByTag(value, tag, baseClone, isDeep);
    }
  }
  // Check for circular references and return its corresponding clone.
  stack || (stack = new Stack);
  var stacked = stack.get(value);
  if (stacked) {
    return stacked;
  }
  stack.set(value, result);

  if (!isArr) {
    var props = isFull ? getAllKeys(value) : keys(value);
  }
  arrayEach(props || value, function(subValue, key) {
    if (props) {
      key = subValue;
      subValue = value[key];
    }
    // Recursively populate clone (susceptible to call stack limits).
    assignValue(result, key, baseClone(subValue, isDeep, isFull, customizer, key, value, stack));
  });
  return result;
}

/**
 * The base implementation of `_.create` without support for assigning
 * properties to the created object.
 *
 * @private
 * @param {Object} prototype The object to inherit from.
 * @returns {Object} Returns the new object.
 */
function baseCreate(proto) {
  return isObject(proto) ? objectCreate(proto) : {};
}

/**
 * The base implementation of `getAllKeys` and `getAllKeysIn` which uses
 * `keysFunc` and `symbolsFunc` to get the enumerable property names and
 * symbols of `object`.
 *
 * @private
 * @param {Object} object The object to query.
 * @param {Function} keysFunc The function to get the keys of `object`.
 * @param {Function} symbolsFunc The function to get the symbols of `object`.
 * @returns {Array} Returns the array of property names and symbols.
 */
function baseGetAllKeys(object, keysFunc, symbolsFunc) {
  var result = keysFunc(object);
  return isArray(object) ? result : arrayPush(result, symbolsFunc(object));
}

/**
 * The base implementation of `getTag`.
 *
 * @private
 * @param {*} value The value to query.
 * @returns {string} Returns the `toStringTag`.
 */
function baseGetTag(value) {
  return objectToString.call(value);
}

/**
 * The base implementation of `_.isNative` without bad shim checks.
 *
 * @private
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is a native function,
 *  else `false`.
 */
function baseIsNative(value) {
  if (!isObject(value) || isMasked(value)) {
    return false;
  }
  var pattern = (isFunction(value) || isHostObject(value)) ? reIsNative : reIsHostCtor;
  return pattern.test(toSource(value));
}

/**
 * The base implementation of `_.keys` which doesn't treat sparse arrays as dense.
 *
 * @private
 * @param {Object} object The object to query.
 * @returns {Array} Returns the array of property names.
 */
function baseKeys(object) {
  if (!isPrototype(object)) {
    return nativeKeys(object);
  }
  var result = [];
  for (var key in Object(object)) {
    if (hasOwnProperty.call(object, key) && key != 'constructor') {
      result.push(key);
    }
  }
  return result;
}

/**
 * Creates a clone of  `buffer`.
 *
 * @private
 * @param {Buffer} buffer The buffer to clone.
 * @param {boolean} [isDeep] Specify a deep clone.
 * @returns {Buffer} Returns the cloned buffer.
 */
function cloneBuffer(buffer, isDeep) {
  if (isDeep) {
    return buffer.slice();
  }
  var result = new buffer.constructor(buffer.length);
  buffer.copy(result);
  return result;
}

/**
 * Creates a clone of `arrayBuffer`.
 *
 * @private
 * @param {ArrayBuffer} arrayBuffer The array buffer to clone.
 * @returns {ArrayBuffer} Returns the cloned array buffer.
 */
function cloneArrayBuffer(arrayBuffer) {
  var result = new arrayBuffer.constructor(arrayBuffer.byteLength);
  new Uint8Array(result).set(new Uint8Array(arrayBuffer));
  return result;
}

/**
 * Creates a clone of `dataView`.
 *
 * @private
 * @param {Object} dataView The data view to clone.
 * @param {boolean} [isDeep] Specify a deep clone.
 * @returns {Object} Returns the cloned data view.
 */
function cloneDataView(dataView, isDeep) {
  var buffer = isDeep ? cloneArrayBuffer(dataView.buffer) : dataView.buffer;
  return new dataView.constructor(buffer, dataView.byteOffset, dataView.byteLength);
}

/**
 * Creates a clone of `map`.
 *
 * @private
 * @param {Object} map The map to clone.
 * @param {Function} cloneFunc The function to clone values.
 * @param {boolean} [isDeep] Specify a deep clone.
 * @returns {Object} Returns the cloned map.
 */
function cloneMap(map, isDeep, cloneFunc) {
  var array = isDeep ? cloneFunc(mapToArray(map), true) : mapToArray(map);
  return arrayReduce(array, addMapEntry, new map.constructor);
}

/**
 * Creates a clone of `regexp`.
 *
 * @private
 * @param {Object} regexp The regexp to clone.
 * @returns {Object} Returns the cloned regexp.
 */
function cloneRegExp(regexp) {
  var result = new regexp.constructor(regexp.source, reFlags.exec(regexp));
  result.lastIndex = regexp.lastIndex;
  return result;
}

/**
 * Creates a clone of `set`.
 *
 * @private
 * @param {Object} set The set to clone.
 * @param {Function} cloneFunc The function to clone values.
 * @param {boolean} [isDeep] Specify a deep clone.
 * @returns {Object} Returns the cloned set.
 */
function cloneSet(set, isDeep, cloneFunc) {
  var array = isDeep ? cloneFunc(setToArray(set), true) : setToArray(set);
  return arrayReduce(array, addSetEntry, new set.constructor);
}

/**
 * Creates a clone of the `symbol` object.
 *
 * @private
 * @param {Object} symbol The symbol object to clone.
 * @returns {Object} Returns the cloned symbol object.
 */
function cloneSymbol(symbol) {
  return symbolValueOf ? Object(symbolValueOf.call(symbol)) : {};
}

/**
 * Creates a clone of `typedArray`.
 *
 * @private
 * @param {Object} typedArray The typed array to clone.
 * @param {boolean} [isDeep] Specify a deep clone.
 * @returns {Object} Returns the cloned typed array.
 */
function cloneTypedArray(typedArray, isDeep) {
  var buffer = isDeep ? cloneArrayBuffer(typedArray.buffer) : typedArray.buffer;
  return new typedArray.constructor(buffer, typedArray.byteOffset, typedArray.length);
}

/**
 * Copies the values of `source` to `array`.
 *
 * @private
 * @param {Array} source The array to copy values from.
 * @param {Array} [array=[]] The array to copy values to.
 * @returns {Array} Returns `array`.
 */
function copyArray(source, array) {
  var index = -1,
      length = source.length;

  array || (array = Array(length));
  while (++index < length) {
    array[index] = source[index];
  }
  return array;
}

/**
 * Copies properties of `source` to `object`.
 *
 * @private
 * @param {Object} source The object to copy properties from.
 * @param {Array} props The property identifiers to copy.
 * @param {Object} [object={}] The object to copy properties to.
 * @param {Function} [customizer] The function to customize copied values.
 * @returns {Object} Returns `object`.
 */
function copyObject(source, props, object, customizer) {
  object || (object = {});

  var index = -1,
      length = props.length;

  while (++index < length) {
    var key = props[index];

    var newValue = customizer
      ? customizer(object[key], source[key], key, object, source)
      : undefined;

    assignValue(object, key, newValue === undefined ? source[key] : newValue);
  }
  return object;
}

/**
 * Copies own symbol properties of `source` to `object`.
 *
 * @private
 * @param {Object} source The object to copy symbols from.
 * @param {Object} [object={}] The object to copy symbols to.
 * @returns {Object} Returns `object`.
 */
function copySymbols(source, object) {
  return copyObject(source, getSymbols(source), object);
}

/**
 * Creates an array of own enumerable property names and symbols of `object`.
 *
 * @private
 * @param {Object} object The object to query.
 * @returns {Array} Returns the array of property names and symbols.
 */
function getAllKeys(object) {
  return baseGetAllKeys(object, keys, getSymbols);
}

/**
 * Gets the data for `map`.
 *
 * @private
 * @param {Object} map The map to query.
 * @param {string} key The reference key.
 * @returns {*} Returns the map data.
 */
function getMapData(map, key) {
  var data = map.__data__;
  return isKeyable(key)
    ? data[typeof key == 'string' ? 'string' : 'hash']
    : data.map;
}

/**
 * Gets the native function at `key` of `object`.
 *
 * @private
 * @param {Object} object The object to query.
 * @param {string} key The key of the method to get.
 * @returns {*} Returns the function if it's native, else `undefined`.
 */
function getNative(object, key) {
  var value = getValue(object, key);
  return baseIsNative(value) ? value : undefined;
}

/**
 * Creates an array of the own enumerable symbol properties of `object`.
 *
 * @private
 * @param {Object} object The object to query.
 * @returns {Array} Returns the array of symbols.
 */
var getSymbols = nativeGetSymbols ? overArg(nativeGetSymbols, Object) : stubArray;

/**
 * Gets the `toStringTag` of `value`.
 *
 * @private
 * @param {*} value The value to query.
 * @returns {string} Returns the `toStringTag`.
 */
var getTag = baseGetTag;

// Fallback for data views, maps, sets, and weak maps in IE 11,
// for data views in Edge < 14, and promises in Node.js.
if ((DataView && getTag(new DataView(new ArrayBuffer(1))) != dataViewTag) ||
    (Map && getTag(new Map) != mapTag) ||
    (Promise && getTag(Promise.resolve()) != promiseTag) ||
    (Set && getTag(new Set) != setTag) ||
    (WeakMap && getTag(new WeakMap) != weakMapTag)) {
  getTag = function(value) {
    var result = objectToString.call(value),
        Ctor = result == objectTag ? value.constructor : undefined,
        ctorString = Ctor ? toSource(Ctor) : undefined;

    if (ctorString) {
      switch (ctorString) {
        case dataViewCtorString: return dataViewTag;
        case mapCtorString: return mapTag;
        case promiseCtorString: return promiseTag;
        case setCtorString: return setTag;
        case weakMapCtorString: return weakMapTag;
      }
    }
    return result;
  };
}

/**
 * Initializes an array clone.
 *
 * @private
 * @param {Array} array The array to clone.
 * @returns {Array} Returns the initialized clone.
 */
function initCloneArray(array) {
  var length = array.length,
      result = array.constructor(length);

  // Add properties assigned by `RegExp#exec`.
  if (length && typeof array[0] == 'string' && hasOwnProperty.call(array, 'index')) {
    result.index = array.index;
    result.input = array.input;
  }
  return result;
}

/**
 * Initializes an object clone.
 *
 * @private
 * @param {Object} object The object to clone.
 * @returns {Object} Returns the initialized clone.
 */
function initCloneObject(object) {
  return (typeof object.constructor == 'function' && !isPrototype(object))
    ? baseCreate(getPrototype(object))
    : {};
}

/**
 * Initializes an object clone based on its `toStringTag`.
 *
 * **Note:** This function only supports cloning values with tags of
 * `Boolean`, `Date`, `Error`, `Number`, `RegExp`, or `String`.
 *
 * @private
 * @param {Object} object The object to clone.
 * @param {string} tag The `toStringTag` of the object to clone.
 * @param {Function} cloneFunc The function to clone values.
 * @param {boolean} [isDeep] Specify a deep clone.
 * @returns {Object} Returns the initialized clone.
 */
function initCloneByTag(object, tag, cloneFunc, isDeep) {
  var Ctor = object.constructor;
  switch (tag) {
    case arrayBufferTag:
      return cloneArrayBuffer(object);

    case boolTag:
    case dateTag:
      return new Ctor(+object);

    case dataViewTag:
      return cloneDataView(object, isDeep);

    case float32Tag: case float64Tag:
    case int8Tag: case int16Tag: case int32Tag:
    case uint8Tag: case uint8ClampedTag: case uint16Tag: case uint32Tag:
      return cloneTypedArray(object, isDeep);

    case mapTag:
      return cloneMap(object, isDeep, cloneFunc);

    case numberTag:
    case stringTag:
      return new Ctor(object);

    case regexpTag:
      return cloneRegExp(object);

    case setTag:
      return cloneSet(object, isDeep, cloneFunc);

    case symbolTag:
      return cloneSymbol(object);
  }
}

/**
 * Checks if `value` is a valid array-like index.
 *
 * @private
 * @param {*} value The value to check.
 * @param {number} [length=MAX_SAFE_INTEGER] The upper bounds of a valid index.
 * @returns {boolean} Returns `true` if `value` is a valid index, else `false`.
 */
function isIndex(value, length) {
  length = length == null ? MAX_SAFE_INTEGER : length;
  return !!length &&
    (typeof value == 'number' || reIsUint.test(value)) &&
    (value > -1 && value % 1 == 0 && value < length);
}

/**
 * Checks if `value` is suitable for use as unique object key.
 *
 * @private
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is suitable, else `false`.
 */
function isKeyable(value) {
  var type = typeof value;
  return (type == 'string' || type == 'number' || type == 'symbol' || type == 'boolean')
    ? (value !== '__proto__')
    : (value === null);
}

/**
 * Checks if `func` has its source masked.
 *
 * @private
 * @param {Function} func The function to check.
 * @returns {boolean} Returns `true` if `func` is masked, else `false`.
 */
function isMasked(func) {
  return !!maskSrcKey && (maskSrcKey in func);
}

/**
 * Checks if `value` is likely a prototype object.
 *
 * @private
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is a prototype, else `false`.
 */
function isPrototype(value) {
  var Ctor = value && value.constructor,
      proto = (typeof Ctor == 'function' && Ctor.prototype) || objectProto;

  return value === proto;
}

/**
 * Converts `func` to its source code.
 *
 * @private
 * @param {Function} func The function to process.
 * @returns {string} Returns the source code.
 */
function toSource(func) {
  if (func != null) {
    try {
      return funcToString.call(func);
    } catch (e) {}
    try {
      return (func + '');
    } catch (e) {}
  }
  return '';
}

/**
 * This method is like `_.clone` except that it recursively clones `value`.
 *
 * @static
 * @memberOf _
 * @since 1.0.0
 * @category Lang
 * @param {*} value The value to recursively clone.
 * @returns {*} Returns the deep cloned value.
 * @see _.clone
 * @example
 *
 * var objects = [{ 'a': 1 }, { 'b': 2 }];
 *
 * var deep = _.cloneDeep(objects);
 * console.log(deep[0] === objects[0]);
 * // => false
 */
function cloneDeep(value) {
  return baseClone(value, true, true);
}

/**
 * Performs a
 * [`SameValueZero`](http://ecma-international.org/ecma-262/7.0/#sec-samevaluezero)
 * comparison between two values to determine if they are equivalent.
 *
 * @static
 * @memberOf _
 * @since 4.0.0
 * @category Lang
 * @param {*} value The value to compare.
 * @param {*} other The other value to compare.
 * @returns {boolean} Returns `true` if the values are equivalent, else `false`.
 * @example
 *
 * var object = { 'a': 1 };
 * var other = { 'a': 1 };
 *
 * _.eq(object, object);
 * // => true
 *
 * _.eq(object, other);
 * // => false
 *
 * _.eq('a', 'a');
 * // => true
 *
 * _.eq('a', Object('a'));
 * // => false
 *
 * _.eq(NaN, NaN);
 * // => true
 */
function eq(value, other) {
  return value === other || (value !== value && other !== other);
}

/**
 * Checks if `value` is likely an `arguments` object.
 *
 * @static
 * @memberOf _
 * @since 0.1.0
 * @category Lang
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is an `arguments` object,
 *  else `false`.
 * @example
 *
 * _.isArguments(function() { return arguments; }());
 * // => true
 *
 * _.isArguments([1, 2, 3]);
 * // => false
 */
function isArguments(value) {
  // Safari 8.1 makes `arguments.callee` enumerable in strict mode.
  return isArrayLikeObject(value) && hasOwnProperty.call(value, 'callee') &&
    (!propertyIsEnumerable.call(value, 'callee') || objectToString.call(value) == argsTag);
}

/**
 * Checks if `value` is classified as an `Array` object.
 *
 * @static
 * @memberOf _
 * @since 0.1.0
 * @category Lang
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is an array, else `false`.
 * @example
 *
 * _.isArray([1, 2, 3]);
 * // => true
 *
 * _.isArray(document.body.children);
 * // => false
 *
 * _.isArray('abc');
 * // => false
 *
 * _.isArray(_.noop);
 * // => false
 */
var isArray = Array.isArray;

/**
 * Checks if `value` is array-like. A value is considered array-like if it's
 * not a function and has a `value.length` that's an integer greater than or
 * equal to `0` and less than or equal to `Number.MAX_SAFE_INTEGER`.
 *
 * @static
 * @memberOf _
 * @since 4.0.0
 * @category Lang
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is array-like, else `false`.
 * @example
 *
 * _.isArrayLike([1, 2, 3]);
 * // => true
 *
 * _.isArrayLike(document.body.children);
 * // => true
 *
 * _.isArrayLike('abc');
 * // => true
 *
 * _.isArrayLike(_.noop);
 * // => false
 */
function isArrayLike(value) {
  return value != null && isLength(value.length) && !isFunction(value);
}

/**
 * This method is like `_.isArrayLike` except that it also checks if `value`
 * is an object.
 *
 * @static
 * @memberOf _
 * @since 4.0.0
 * @category Lang
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is an array-like object,
 *  else `false`.
 * @example
 *
 * _.isArrayLikeObject([1, 2, 3]);
 * // => true
 *
 * _.isArrayLikeObject(document.body.children);
 * // => true
 *
 * _.isArrayLikeObject('abc');
 * // => false
 *
 * _.isArrayLikeObject(_.noop);
 * // => false
 */
function isArrayLikeObject(value) {
  return isObjectLike(value) && isArrayLike(value);
}

/**
 * Checks if `value` is a buffer.
 *
 * @static
 * @memberOf _
 * @since 4.3.0
 * @category Lang
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is a buffer, else `false`.
 * @example
 *
 * _.isBuffer(new Buffer(2));
 * // => true
 *
 * _.isBuffer(new Uint8Array(2));
 * // => false
 */
var isBuffer = nativeIsBuffer || stubFalse;

/**
 * Checks if `value` is classified as a `Function` object.
 *
 * @static
 * @memberOf _
 * @since 0.1.0
 * @category Lang
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is a function, else `false`.
 * @example
 *
 * _.isFunction(_);
 * // => true
 *
 * _.isFunction(/abc/);
 * // => false
 */
function isFunction(value) {
  // The use of `Object#toString` avoids issues with the `typeof` operator
  // in Safari 8-9 which returns 'object' for typed array and other constructors.
  var tag = isObject(value) ? objectToString.call(value) : '';
  return tag == funcTag || tag == genTag;
}

/**
 * Checks if `value` is a valid array-like length.
 *
 * **Note:** This method is loosely based on
 * [`ToLength`](http://ecma-international.org/ecma-262/7.0/#sec-tolength).
 *
 * @static
 * @memberOf _
 * @since 4.0.0
 * @category Lang
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is a valid length, else `false`.
 * @example
 *
 * _.isLength(3);
 * // => true
 *
 * _.isLength(Number.MIN_VALUE);
 * // => false
 *
 * _.isLength(Infinity);
 * // => false
 *
 * _.isLength('3');
 * // => false
 */
function isLength(value) {
  return typeof value == 'number' &&
    value > -1 && value % 1 == 0 && value <= MAX_SAFE_INTEGER;
}

/**
 * Checks if `value` is the
 * [language type](http://www.ecma-international.org/ecma-262/7.0/#sec-ecmascript-language-types)
 * of `Object`. (e.g. arrays, functions, objects, regexes, `new Number(0)`, and `new String('')`)
 *
 * @static
 * @memberOf _
 * @since 0.1.0
 * @category Lang
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is an object, else `false`.
 * @example
 *
 * _.isObject({});
 * // => true
 *
 * _.isObject([1, 2, 3]);
 * // => true
 *
 * _.isObject(_.noop);
 * // => true
 *
 * _.isObject(null);
 * // => false
 */
function isObject(value) {
  var type = typeof value;
  return !!value && (type == 'object' || type == 'function');
}

/**
 * Checks if `value` is object-like. A value is object-like if it's not `null`
 * and has a `typeof` result of "object".
 *
 * @static
 * @memberOf _
 * @since 4.0.0
 * @category Lang
 * @param {*} value The value to check.
 * @returns {boolean} Returns `true` if `value` is object-like, else `false`.
 * @example
 *
 * _.isObjectLike({});
 * // => true
 *
 * _.isObjectLike([1, 2, 3]);
 * // => true
 *
 * _.isObjectLike(_.noop);
 * // => false
 *
 * _.isObjectLike(null);
 * // => false
 */
function isObjectLike(value) {
  return !!value && typeof value == 'object';
}

/**
 * Creates an array of the own enumerable property names of `object`.
 *
 * **Note:** Non-object values are coerced to objects. See the
 * [ES spec](http://ecma-international.org/ecma-262/7.0/#sec-object.keys)
 * for more details.
 *
 * @static
 * @since 0.1.0
 * @memberOf _
 * @category Object
 * @param {Object} object The object to query.
 * @returns {Array} Returns the array of property names.
 * @example
 *
 * function Foo() {
 *   this.a = 1;
 *   this.b = 2;
 * }
 *
 * Foo.prototype.c = 3;
 *
 * _.keys(new Foo);
 * // => ['a', 'b'] (iteration order is not guaranteed)
 *
 * _.keys('hi');
 * // => ['0', '1']
 */
function keys(object) {
  return isArrayLike(object) ? arrayLikeKeys(object) : baseKeys(object);
}

/**
 * This method returns a new empty array.
 *
 * @static
 * @memberOf _
 * @since 4.13.0
 * @category Util
 * @returns {Array} Returns the new empty array.
 * @example
 *
 * var arrays = _.times(2, _.stubArray);
 *
 * console.log(arrays);
 * // => [[], []]
 *
 * console.log(arrays[0] === arrays[1]);
 * // => false
 */
function stubArray() {
  return [];
}

/**
 * This method returns `false`.
 *
 * @static
 * @memberOf _
 * @since 4.13.0
 * @category Util
 * @returns {boolean} Returns `false`.
 * @example
 *
 * _.times(2, _.stubFalse);
 * // => [false, false]
 */
function stubFalse() {
  return false;
}

module.exports = cloneDeep;

}).call(this)}).call(this,typeof global !== "undefined" ? global : typeof self !== "undefined" ? self : typeof window !== "undefined" ? window : {})
},{}],71:[function(require,module,exports){
/**
 * Helpers.
 */

var s = 1000;
var m = s * 60;
var h = m * 60;
var d = h * 24;
var y = d * 365.25;

/**
 * Parse or format the given `val`.
 *
 * Options:
 *
 *  - `long` verbose formatting [false]
 *
 * @param {String|Number} val
 * @param {Object} [options]
 * @throws {Error} throw an error if val is not a non-empty string or a number
 * @return {String|Number}
 * @api public
 */

module.exports = function(val, options) {
  options = options || {};
  var type = typeof val;
  if (type === 'string' && val.length > 0) {
    return parse(val);
  } else if (type === 'number' && isNaN(val) === false) {
    return options.long ? fmtLong(val) : fmtShort(val);
  }
  throw new Error(
    'val is not a non-empty string or a valid number. val=' +
      JSON.stringify(val)
  );
};

/**
 * Parse the given `str` and return milliseconds.
 *
 * @param {String} str
 * @return {Number}
 * @api private
 */

function parse(str) {
  str = String(str);
  if (str.length > 100) {
    return;
  }
  var match = /^((?:\d+)?\.?\d+) *(milliseconds?|msecs?|ms|seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|days?|d|years?|yrs?|y)?$/i.exec(
    str
  );
  if (!match) {
    return;
  }
  var n = parseFloat(match[1]);
  var type = (match[2] || 'ms').toLowerCase();
  switch (type) {
    case 'years':
    case 'year':
    case 'yrs':
    case 'yr':
    case 'y':
      return n * y;
    case 'days':
    case 'day':
    case 'd':
      return n * d;
    case 'hours':
    case 'hour':
    case 'hrs':
    case 'hr':
    case 'h':
      return n * h;
    case 'minutes':
    case 'minute':
    case 'mins':
    case 'min':
    case 'm':
      return n * m;
    case 'seconds':
    case 'second':
    case 'secs':
    case 'sec':
    case 's':
      return n * s;
    case 'milliseconds':
    case 'millisecond':
    case 'msecs':
    case 'msec':
    case 'ms':
      return n;
    default:
      return undefined;
  }
}

/**
 * Short format for `ms`.
 *
 * @param {Number} ms
 * @return {String}
 * @api private
 */

function fmtShort(ms) {
  if (ms >= d) {
    return Math.round(ms / d) + 'd';
  }
  if (ms >= h) {
    return Math.round(ms / h) + 'h';
  }
  if (ms >= m) {
    return Math.round(ms / m) + 'm';
  }
  if (ms >= s) {
    return Math.round(ms / s) + 's';
  }
  return ms + 'ms';
}

/**
 * Long format for `ms`.
 *
 * @param {Number} ms
 * @return {String}
 * @api private
 */

function fmtLong(ms) {
  return plural(ms, d, 'day') ||
    plural(ms, h, 'hour') ||
    plural(ms, m, 'minute') ||
    plural(ms, s, 'second') ||
    ms + ' ms';
}

/**
 * Pluralization helper.
 */

function plural(ms, n, name) {
  if (ms < n) {
    return;
  }
  if (ms < n * 1.5) {
    return Math.floor(ms / n) + ' ' + name;
  }
  return Math.ceil(ms / n) + ' ' + name + 's';
}

},{}],72:[function(require,module,exports){
'use strict';

var is = require('is');
var isodate = require('@segment/isodate');
var milliseconds = require('./milliseconds');
var seconds = require('./seconds');

/**
 * Returns a new Javascript Date object, allowing a variety of extra input types
 * over the native Date constructor.
 *
 * @param {Date|string|number} val
 */
module.exports = function newDate(val) {
  if (is.date(val)) return val;
  if (is.number(val)) return new Date(toMs(val));

  // date strings
  if (isodate.is(val)) {
    return isodate.parse(val);
  }
  if (milliseconds.is(val)) {
    return milliseconds.parse(val);
  }
  if (seconds.is(val)) {
    return seconds.parse(val);
  }

  // fallback to Date.parse
  return new Date(val);
};


/**
 * If the number passed val is seconds from the epoch, turn it into milliseconds.
 * Milliseconds would be greater than 31557600000 (December 31, 1970).
 *
 * @param {number} num
 */
function toMs(num) {
  if (num < 31557600000) return num * 1000;
  return num;
}

},{"./milliseconds":73,"./seconds":74,"@segment/isodate":75,"is":66}],73:[function(require,module,exports){
'use strict';

/**
 * Matcher.
 */

var matcher = /\d{13}/;


/**
 * Check whether a string is a millisecond date string.
 *
 * @param {string} string
 * @return {boolean}
 */
exports.is = function(string) {
  return matcher.test(string);
};


/**
 * Convert a millisecond string to a date.
 *
 * @param {string} millis
 * @return {Date}
 */
exports.parse = function(millis) {
  millis = parseInt(millis, 10);
  return new Date(millis);
};

},{}],74:[function(require,module,exports){
'use strict';

/**
 * Matcher.
 */

var matcher = /\d{10}/;


/**
 * Check whether a string is a second date string.
 *
 * @param {string} string
 * @return {Boolean}
 */
exports.is = function(string) {
  return matcher.test(string);
};


/**
 * Convert a second string to a date.
 *
 * @param {string} seconds
 * @return {Date}
 */
exports.parse = function(seconds) {
  var millis = parseInt(seconds, 10) * 1000;
  return new Date(millis);
};

},{}],75:[function(require,module,exports){
'use strict';

/**
 * Matcher, slightly modified from:
 *
 * https://github.com/csnover/js-iso8601/blob/lax/iso8601.js
 */

var matcher = /^(\d{4})(?:-?(\d{2})(?:-?(\d{2}))?)?(?:([ T])(\d{2}):?(\d{2})(?::?(\d{2})(?:[,\.](\d{1,}))?)?(?:(Z)|([+\-])(\d{2})(?::?(\d{2}))?)?)?$/;

/**
 * Convert an ISO date string to a date. Fallback to native `Date.parse`.
 *
 * https://github.com/csnover/js-iso8601/blob/lax/iso8601.js
 *
 * @param {String} iso
 * @return {Date}
 */

exports.parse = function(iso) {
  var numericKeys = [1, 5, 6, 7, 11, 12];
  var arr = matcher.exec(iso);
  var offset = 0;

  // fallback to native parsing
  if (!arr) {
    return new Date(iso);
  }

  /* eslint-disable no-cond-assign */
  // remove undefined values
  for (var i = 0, val; val = numericKeys[i]; i++) {
    arr[val] = parseInt(arr[val], 10) || 0;
  }
  /* eslint-enable no-cond-assign */

  // allow undefined days and months
  arr[2] = parseInt(arr[2], 10) || 1;
  arr[3] = parseInt(arr[3], 10) || 1;

  // month is 0-11
  arr[2]--;

  // allow abitrary sub-second precision
  arr[8] = arr[8] ? (arr[8] + '00').substring(0, 3) : 0;

  // apply timezone if one exists
  if (arr[4] === ' ') {
    offset = new Date().getTimezoneOffset();
  } else if (arr[9] !== 'Z' && arr[10]) {
    offset = arr[11] * 60 + arr[12];
    if (arr[10] === '+') {
      offset = 0 - offset;
    }
  }

  var millis = Date.UTC(arr[1], arr[2], arr[3], arr[5], arr[6] + offset, arr[7], arr[8]);
  return new Date(millis);
};


/**
 * Checks whether a `string` is an ISO date string. `strict` mode requires that
 * the date string at least have a year, month and date.
 *
 * @param {String} string
 * @param {Boolean} strict
 * @return {Boolean}
 */

exports.is = function(string, strict) {
  if (strict && (/^\d{4}-\d{2}-\d{2}/).test(string) === false) {
    return false;
  }
  return matcher.test(string);
};

},{}],76:[function(require,module,exports){
(function (process,setImmediate){(function (){
'use strict';

var callable, byObserver;

callable = function (fn) {
	if (typeof fn !== 'function') throw new TypeError(fn + " is not a function");
	return fn;
};

byObserver = function (Observer) {
	var node = document.createTextNode(''), queue, i = 0;
	new Observer(function () {
		var data;
		if (!queue) return;
		data = queue;
		queue = null;
		if (typeof data === 'function') {
			data();
			return;
		}
		data.forEach(function (fn) { fn(); });
	}).observe(node, { characterData: true });
	return function (fn) {
		callable(fn);
		if (queue) {
			if (typeof queue === 'function') queue = [queue, fn];
			else queue.push(fn);
			return;
		}
		queue = fn;
		node.data = (i = ++i % 2);
	};
};

module.exports = (function () {
	// Node.js
	if ((typeof process !== 'undefined') && process &&
			(typeof process.nextTick === 'function')) {
		return process.nextTick;
	}

	// MutationObserver=
	if ((typeof document === 'object') && document) {
		if (typeof MutationObserver === 'function') {
			return byObserver(MutationObserver);
		}
		if (typeof WebKitMutationObserver === 'function') {
			return byObserver(WebKitMutationObserver);
		}
	}

	// W3C Draft
	// http://dvcs.w3.org/hg/webperf/raw-file/tip/specs/setImmediate/Overview.html
	if (typeof setImmediate === 'function') {
		return function (cb) { setImmediate(callable(cb)); };
	}

	// Wide available standard
	if (typeof setTimeout === 'function') {
		return function (cb) { setTimeout(callable(cb), 0); };
	}

	return null;
}());

}).call(this)}).call(this,require('_process'),require("timers").setImmediate)
},{"_process":78,"timers":94}],77:[function(require,module,exports){

var identity = function(_){ return _; };


/**
 * Module exports, export
 */

module.exports = multiple(find);
module.exports.find = module.exports;


/**
 * Export the replacement function, return the modified object
 */

module.exports.replace = function (obj, key, val, options) {
  multiple(replace).call(this, obj, key, val, options);
  return obj;
};


/**
 * Export the delete function, return the modified object
 */

module.exports.del = function (obj, key, options) {
  multiple(del).call(this, obj, key, null, options);
  return obj;
};


/**
 * Compose applying the function to a nested key
 */

function multiple (fn) {
  return function (obj, path, val, options) {
    normalize = options && isFunction(options.normalizer) ? options.normalizer : defaultNormalize;
    path = normalize(path);

    var key;
    var finished = false;

    while (!finished) loop();

    function loop() {
      for (key in obj) {
        var normalizedKey = normalize(key);
        if (0 === path.indexOf(normalizedKey)) {
          var temp = path.substr(normalizedKey.length);
          if (temp.charAt(0) === '.' || temp.length === 0) {
            path = temp.substr(1);
            var child = obj[key];

            // we're at the end and there is nothing.
            if (null == child) {
              finished = true;
              return;
            }

            // we're at the end and there is something.
            if (!path.length) {
              finished = true;
              return;
            }

            // step into child
            obj = child;

            // but we're done here
            return;
          }
        }
      }

      key = undefined;
      // if we found no matching properties
      // on the current object, there's no match.
      finished = true;
    }

    if (!key) return;
    if (null == obj) return obj;

    // the `obj` and `key` is one above the leaf object and key, so
    // start object: { a: { 'b.c': 10 } }
    // end object: { 'b.c': 10 }
    // end key: 'b.c'
    // this way, you can do `obj[key]` and get `10`.
    return fn(obj, key, val);
  };
}


/**
 * Find an object by its key
 *
 * find({ first_name : 'Calvin' }, 'firstName')
 */

function find (obj, key) {
  if (obj.hasOwnProperty(key)) return obj[key];
}


/**
 * Delete a value for a given key
 *
 * del({ a : 'b', x : 'y' }, 'X' }) -> { a : 'b' }
 */

function del (obj, key) {
  if (obj.hasOwnProperty(key)) delete obj[key];
  return obj;
}


/**
 * Replace an objects existing value with a new one
 *
 * replace({ a : 'b' }, 'a', 'c') -> { a : 'c' }
 */

function replace (obj, key, val) {
  if (obj.hasOwnProperty(key)) obj[key] = val;
  return obj;
}

/**
 * Normalize a `dot.separated.path`.
 *
 * A.HELL(!*&#(!)O_WOR   LD.bar => ahelloworldbar
 *
 * @param {String} path
 * @return {String}
 */

function defaultNormalize(path) {
  return path.replace(/[^a-zA-Z0-9\.]+/g, '').toLowerCase();
}

/**
 * Check if a value is a function.
 *
 * @param {*} val
 * @return {boolean} Returns `true` if `val` is a function, otherwise `false`.
 */

function isFunction(val) {
  return typeof val === 'function';
}

},{}],78:[function(require,module,exports){
// shim for using process in browser
var process = module.exports = {};

// cached from whatever global is present so that test runners that stub it
// don't break things.  But we need to wrap it in a try catch in case it is
// wrapped in strict mode code which doesn't define any globals.  It's inside a
// function because try/catches deoptimize in certain engines.

var cachedSetTimeout;
var cachedClearTimeout;

function defaultSetTimout() {
    throw new Error('setTimeout has not been defined');
}
function defaultClearTimeout () {
    throw new Error('clearTimeout has not been defined');
}
(function () {
    try {
        if (typeof setTimeout === 'function') {
            cachedSetTimeout = setTimeout;
        } else {
            cachedSetTimeout = defaultSetTimout;
        }
    } catch (e) {
        cachedSetTimeout = defaultSetTimout;
    }
    try {
        if (typeof clearTimeout === 'function') {
            cachedClearTimeout = clearTimeout;
        } else {
            cachedClearTimeout = defaultClearTimeout;
        }
    } catch (e) {
        cachedClearTimeout = defaultClearTimeout;
    }
} ())
function runTimeout(fun) {
    if (cachedSetTimeout === setTimeout) {
        //normal enviroments in sane situations
        return setTimeout(fun, 0);
    }
    // if setTimeout wasn't available but was latter defined
    if ((cachedSetTimeout === defaultSetTimout || !cachedSetTimeout) && setTimeout) {
        cachedSetTimeout = setTimeout;
        return setTimeout(fun, 0);
    }
    try {
        // when when somebody has screwed with setTimeout but no I.E. maddness
        return cachedSetTimeout(fun, 0);
    } catch(e){
        try {
            // When we are in I.E. but the script has been evaled so I.E. doesn't trust the global object when called normally
            return cachedSetTimeout.call(null, fun, 0);
        } catch(e){
            // same as above but when it's a version of I.E. that must have the global object for 'this', hopfully our context correct otherwise it will throw a global error
            return cachedSetTimeout.call(this, fun, 0);
        }
    }


}
function runClearTimeout(marker) {
    if (cachedClearTimeout === clearTimeout) {
        //normal enviroments in sane situations
        return clearTimeout(marker);
    }
    // if clearTimeout wasn't available but was latter defined
    if ((cachedClearTimeout === defaultClearTimeout || !cachedClearTimeout) && clearTimeout) {
        cachedClearTimeout = clearTimeout;
        return clearTimeout(marker);
    }
    try {
        // when when somebody has screwed with setTimeout but no I.E. maddness
        return cachedClearTimeout(marker);
    } catch (e){
        try {
            // When we are in I.E. but the script has been evaled so I.E. doesn't  trust the global object when called normally
            return cachedClearTimeout.call(null, marker);
        } catch (e){
            // same as above but when it's a version of I.E. that must have the global object for 'this', hopfully our context correct otherwise it will throw a global error.
            // Some versions of I.E. have different rules for clearTimeout vs setTimeout
            return cachedClearTimeout.call(this, marker);
        }
    }



}
var queue = [];
var draining = false;
var currentQueue;
var queueIndex = -1;

function cleanUpNextTick() {
    if (!draining || !currentQueue) {
        return;
    }
    draining = false;
    if (currentQueue.length) {
        queue = currentQueue.concat(queue);
    } else {
        queueIndex = -1;
    }
    if (queue.length) {
        drainQueue();
    }
}

function drainQueue() {
    if (draining) {
        return;
    }
    var timeout = runTimeout(cleanUpNextTick);
    draining = true;

    var len = queue.length;
    while(len) {
        currentQueue = queue;
        queue = [];
        while (++queueIndex < len) {
            if (currentQueue) {
                currentQueue[queueIndex].run();
            }
        }
        queueIndex = -1;
        len = queue.length;
    }
    currentQueue = null;
    draining = false;
    runClearTimeout(timeout);
}

process.nextTick = function (fun) {
    var args = new Array(arguments.length - 1);
    if (arguments.length > 1) {
        for (var i = 1; i < arguments.length; i++) {
            args[i - 1] = arguments[i];
        }
    }
    queue.push(new Item(fun, args));
    if (queue.length === 1 && !draining) {
        runTimeout(drainQueue);
    }
};

// v8 likes predictible objects
function Item(fun, array) {
    this.fun = fun;
    this.array = array;
}
Item.prototype.run = function () {
    this.fun.apply(null, this.array);
};
process.title = 'browser';
process.browser = true;
process.env = {};
process.argv = [];
process.version = ''; // empty string to avoid regexp issues
process.versions = {};

function noop() {}

process.on = noop;
process.addListener = noop;
process.once = noop;
process.off = noop;
process.removeListener = noop;
process.removeAllListeners = noop;
process.emit = noop;
process.prependListener = noop;
process.prependOnceListener = noop;

process.listeners = function (name) { return [] }

process.binding = function (name) {
    throw new Error('process.binding is not supported');
};

process.cwd = function () { return '/' };
process.chdir = function (dir) {
    throw new Error('process.chdir is not supported');
};
process.umask = function() { return 0; };

},{}],79:[function(require,module,exports){

// https://github.com/thirdpartyjs/thirdpartyjs-code/blob/master/examples/templates/02/loading-files/index.html

/**
 * Invoke `fn(err)` when the given `el` script loads.
 *
 * @param {Element} el
 * @param {Function} fn
 * @api public
 */

module.exports = function(el, fn){
  return el.addEventListener
    ? add(el, fn)
    : attach(el, fn);
};

/**
 * Add event listener to `el`, `fn()`.
 *
 * @param {Element} el
 * @param {Function} fn
 * @api private
 */

function add(el, fn){
  el.addEventListener('load', function(_, e){ fn(null, e); }, false);
  el.addEventListener('error', function(e){
    var err = new Error('script error "' + el.src + '"');
    err.event = e;
    fn(err);
  }, false);
}

/**
 * Attach event.
 *
 * @param {Element} el
 * @param {Function} fn
 * @api private
 */

function attach(el, fn){
  el.attachEvent('onreadystatechange', function(e){
    if (!/complete|loaded/.test(el.readyState)) return;
    fn(null, e);
  });
  el.attachEvent('onerror', function(e){
    var err = new Error('failed to load the script "' + el.src + '"');
    err.event = e || window.event;
    fn(err);
  });
}

},{}],80:[function(require,module,exports){
'use strict';

var get = require('obj-case');

/**
 * Add address getters to `proto`.
 *
 * @ignore
 * @param {Function} proto
 */
module.exports = function(proto) {
  proto.zip = trait('postalCode', 'zip');
  proto.country = trait('country');
  proto.street = trait('street');
  proto.state = trait('state');
  proto.city = trait('city');
  proto.region = trait('region');

  function trait(a, b) {
    return function() {
      var traits = this.traits();
      var props = this.properties ? this.properties() : {};

      return get(traits, 'address.' + a)
        || get(traits, a)
        || (b ? get(traits, 'address.' + b) : null)
        || (b ? get(traits, b) : null)
        || get(props, 'address.' + a)
        || get(props, a)
        || (b ? get(props, 'address.' + b) : null)
        || (b ? get(props, b) : null);
    };
  }
};

},{"obj-case":77}],81:[function(require,module,exports){
'use strict';

var inherit = require('./utils').inherit;
var Facade = require('./facade');

/**
 * Initialize a new `Alias` facade with a `dictionary` of arguments.
 *
 * @param {Object} dictionary - The object to wrap.
 * @param {string} [dictionary.from] - The previous ID of the user.
 * @param {string} [dictionary.to] - The new ID of the user.
 * @param {Object} opts - Options about what kind of Facade to create.
 *
 * @augments Facade
 */
function Alias(dictionary, opts) {
  Facade.call(this, dictionary, opts);
}

inherit(Alias, Facade);

/**
 * Return the type of facade this is. This will always return `"alias"`.
 *
 * @return {string}
 */
Alias.prototype.action = function() {
  return 'alias';
};

/**
 * An alias for {@link Alias#action}.
 *
 * @function
 * @return {string}
 */
Alias.prototype.type = Alias.prototype.action;

/**
 * Get the user's previous ID from `previousId` or `from`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Alias.prototype.previousId = function() {
  return this.field('previousId') || this.field('from');
};

/**
 * An alias for {@link Alias#previousId}.
 *
 * @function
 * @return {string}
 */
Alias.prototype.from = Alias.prototype.previousId;

/**
 * Get the user's new ID from `userId` or `to`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Alias.prototype.userId = function() {
  return this.field('userId') || this.field('to');
};

/**
 * An alias for {@link Alias#userId}.
 *
 * @function
 * @return {string}
 */
Alias.prototype.to = Alias.prototype.userId;

module.exports = Alias;

},{"./facade":83,"./utils":91}],82:[function(require,module,exports){
'use strict';

var inherit = require('./utils').inherit;
var Facade = require('./facade');

/**
 * Initialize a new `Delete` facade with a `dictionary` of arguments.
 *
 * @param {Object} dictionary - The object to wrap.
 * @param {string} [dictionary.category] - The delete category.
 * @param {string} [dictionary.name] - The delete name.
 * @param {string} [dictionary.properties] - The delete properties.
 * @param {Object} opts - Options about what kind of Facade to create.
 *
 * @augments Facade
 */
function Delete(dictionary, opts) {
  Facade.call(this, dictionary, opts);
}

inherit(Delete, Facade);

/**
 * Return the type of facade this is. This will always return `"delete"`.
 *
 * @return {string}
 */
Delete.prototype.type = function() {
  return 'delete';
};

module.exports = Delete;

},{"./facade":83,"./utils":91}],83:[function(require,module,exports){
'use strict';

var address = require('./address');
var clone = require('./utils').clone;
var isEnabled = require('./is-enabled');
var newDate = require('new-date');
var objCase = require('obj-case');
var traverse = require('@segment/isodate-traverse');
var type = require('./utils').type;

/**
 * A *Facade* is an object meant for creating convience wrappers around
 * objects. When developing integrations, you probably want to look at its
 * subclasses, such as {@link Track} or {@link Identify}, rather than this
 * general-purpose class.
 *
 * This constructor will initialize a new `Facade` with an `obj` of arguments.
 *
 * If the inputted `obj` doesn't have a `timestamp` property, one will be added
 * with the value `new Date()`. Otherwise, the `timestamp` property will be
 * converted to a Date using the `new-date` package.
 *
 * By default, the inputted object will be defensively copied, and all ISO
 * strings present in the string will be converted into Dates.
 *
 * @param {Object} obj - The object to wrap.
 * @param {Object} opts - Options about what kind of Facade to create.
 * @param {boolean} [opts.clone=true] - Whether to make defensive clones. If enabled,
 * the inputted object will be cloned, and any objects derived from this facade
 * will be cloned before being returned.
 * @param {boolean} [opts.traverse=true] - Whether to perform ISODate-Traverse
 * on the inputted object.
 *
 * @see {@link https://github.com/segmentio/new-date|new-date}
 * @see {@link https://github.com/segmentio/isodate-traverse|isodate-traverse}
 */
function Facade(obj, opts) {
  opts = opts || {};
  if (!('clone' in opts)) opts.clone = true;
  if (opts.clone) obj = clone(obj);
  if (!('traverse' in opts)) opts.traverse = true;
  if (!('timestamp' in obj)) obj.timestamp = new Date();
  else obj.timestamp = newDate(obj.timestamp);
  if (opts.traverse) traverse(obj);
  this.opts = opts;
  this.obj = obj;
}

/**
 * Get a potentially-nested field in this facade. `field` should be a
 * period-separated sequence of properties.
 *
 * If the first field passed in points to a function (e.g. the `field` passed
 * in is `a.b.c` and this facade's `obj.a` is a function), then that function
 * will be called, and then the deeper fields will be fetched (using obj-case)
 * from what that function returns. If the first field isn't a function, then
 * this function works just like obj-case.
 *
 * Because this function uses obj-case, the camel- or snake-case of the input
 * is irrelevant.
 *
 * @example
 * YourClass.prototype.height = function() {
 *   return this.proxy('getDimensions.height') ||
 *     this.proxy('props.size.side_length');
 * }
 * @param {string} field - A sequence of properties, joined by periods (`.`).
 * @return {*} - A property of the inputted object.
 * @see {@link https://github.com/segmentio/obj-case|obj-case}
 */
Facade.prototype.proxy = function(field) {
  var fields = field.split('.');
  field = fields.shift();

  // Call a function at the beginning to take advantage of facaded fields
  var obj = this[field] || this.field(field);
  if (!obj) return obj;
  if (typeof obj === 'function') obj = obj.call(this) || {};
  if (fields.length === 0) return this.opts.clone ? transform(obj) : obj;

  obj = objCase(obj, fields.join('.'));
  return this.opts.clone ? transform(obj) : obj;
};

/**
 * Directly access a specific `field` from the underlying object. Only
 * "top-level" fields will work with this function. "Nested" fields *will not
 * work* with this function.
 *
 * @param {string} field
 * @return {*}
 */
Facade.prototype.field = function(field) {
  var obj = this.obj[field];
  return this.opts.clone ? transform(obj) : obj;
};

/**
 * Utility method to always proxy a particular `field`. In other words, it
 * returns a function that will always return `this.proxy(field)`.
 *
 * @example
 * MyClass.prototype.height = Facade.proxy('options.dimensions.height');
 *
 * @param {string} field
 * @return {Function}
 */
Facade.proxy = function(field) {
  return function() {
    return this.proxy(field);
  };
};

/**
 * Utility method to always access a `field`. In other words, it returns a
 * function that will always return `this.field(field)`.
 *
 * @param {string} field
 * @return {Function}
 */
Facade.field = function(field) {
  return function() {
    return this.field(field);
  };
};

/**
 * Create a helper function for fetching a "plural" thing.
 *
 * The generated method will take the inputted `path` and append an "s" to it
 * and calls `this.proxy` with this "pluralized" path. If that produces an
 * array, that will be returned. Otherwise, a one-element array containing
 * `this.proxy(path)` will be returned.
 *
 * @example
 * MyClass.prototype.birds = Facade.multi('animals.bird');
 *
 * @param {string} path
 * @return {Function}
 */
Facade.multi = function(path) {
  return function() {
    var multi = this.proxy(path + 's');
    if (type(multi) === 'array') return multi;
    var one = this.proxy(path);
    if (one) one = [this.opts.clone ? clone(one) : one];
    return one || [];
  };
};

/**
 * Create a helper function for getting a "singular" thing.
 *
 * The generated method will take the inputted path and call
 * `this.proxy(path)`. If a truthy thing is produced, it will be returned.
 * Otherwise, `this.proxy(path + 's')` will be called, and if that produces an
 * array the first element of that array will be returned. Otherwise,
 * `undefined` is returned.
 *
 * @example
 * MyClass.prototype.bird = Facade.one('animals.bird');
 *
 * @param {string} path
 * @return {Function}
 */
Facade.one = function(path) {
  return function() {
    var one = this.proxy(path);
    if (one) return one;
    var multi = this.proxy(path + 's');
    if (type(multi) === 'array') return multi[0];
  };
};

/**
 * Gets the underlying object this facade wraps around.
 *
 * If this facade has a property `type`, it will be invoked as a function and
 * will be assigned as the property `type` of the outputted object.
 *
 * @return {Object}
 */
Facade.prototype.json = function() {
  var ret = this.opts.clone ? clone(this.obj) : this.obj;
  if (this.type) ret.type = this.type();
  return ret;
};

/**
 * Get the options of a call. If an integration is passed, only the options for
 * that integration are included. If the integration is not enabled, then
 * `undefined` is returned.
 *
 * Options are taken from the `options` property of the underlying object,
 * falling back to the object's `context` or simply `{}`.
 *
 * @param {string} integration - The name of the integration to get settings
 * for. Casing does not matter.
 * @return {Object|undefined}
 */
Facade.prototype.options = function(integration) {
  var obj = this.obj.options || this.obj.context || {};
  var options = this.opts.clone ? clone(obj) : obj;
  if (!integration) return options;
  if (!this.enabled(integration)) return;
  var integrations = this.integrations();
  var value = integrations[integration] || objCase(integrations, integration);
  if (typeof value !== 'object') value = objCase(this.options(), integration);
  return typeof value === 'object' ? value : {};
};

/**
 * An alias for {@link Facade#options}.
 */
Facade.prototype.context = Facade.prototype.options;

/**
 * Check whether an integration is enabled.
 *
 * Basically, this method checks whether this integration is explicitly
 * enabled. If it isn'texplicitly mentioned, it checks whether it has been
 * enabled at the global level. Some integrations (e.g. Salesforce), cannot
 * enabled by these global event settings.
 *
 * More concretely, the deciding factors here are:
 *
 * 1. If `this.integrations()` has the integration set to `true`, return `true`.
 * 2. If `this.integrations().providers` has the integration set to `true`, return `true`.
 * 3. If integrations are set to default-disabled via global parameters (i.e.
 * `options.providers.all`, `options.all`, or `integrations.all`), then return
 * false.
 * 4. If the integration is one of the special default-deny integrations
 * (currently, only Salesforce), then return false.
 * 5. Else, return true.
 *
 * @param {string} integration
 * @return {boolean}
 */
Facade.prototype.enabled = function(integration) {
  var allEnabled = this.proxy('options.providers.all');
  if (typeof allEnabled !== 'boolean') allEnabled = this.proxy('options.all');
  if (typeof allEnabled !== 'boolean') allEnabled = this.proxy('integrations.all');
  if (typeof allEnabled !== 'boolean') allEnabled = true;

  var enabled = allEnabled && isEnabled(integration);
  var options = this.integrations();

  // If the integration is explicitly enabled or disabled, use that
  // First, check options.providers for backwards compatibility
  if (options.providers && options.providers.hasOwnProperty(integration)) {
    enabled = options.providers[integration];
  }

  // Next, check for the integration's existence in 'options' to enable it.
  // If the settings are a boolean, use that, otherwise it should be enabled.
  if (options.hasOwnProperty(integration)) {
    var settings = options[integration];
    if (typeof settings === 'boolean') {
      enabled = settings;
    } else {
      enabled = true;
    }
  }

  return !!enabled;
};

/**
 * Get all `integration` options.
 *
 * @ignore
 * @param {string} integration
 * @return {Object}
 */
Facade.prototype.integrations = function() {
  return this.obj.integrations || this.proxy('options.providers') || this.options();
};

/**
 * Check whether the user is active.
 *
 * @return {boolean}
 */
Facade.prototype.active = function() {
  var active = this.proxy('options.active');
  if (active === null || active === undefined) active = true;
  return active;
};

/**
 * Get `sessionId / anonymousId`.
 *
 * @return {*}
 */
Facade.prototype.anonymousId = function() {
  return this.field('anonymousId') || this.field('sessionId');
};

/**
 * An alias for {@link Facade#anonymousId}.
 *
 * @function
 * @return {string}
 */
Facade.prototype.sessionId = Facade.prototype.anonymousId;

/**
 * Get `groupId` from `context.groupId`.
 *
 * @function
 * @return {string}
 */
Facade.prototype.groupId = Facade.proxy('options.groupId');

/**
 * Get the call's "traits". All event types can pass in traits, though {@link
 * Identify} and {@link Group} override this implementation.
 *
 * Traits are gotten from `options.traits`, augmented with a property `id` with
 * the event's `userId`.
 *
 * The parameter `aliases` is meant to transform keys in `options.traits` into
 * new keys. Each alias like `{ "xxx": "yyy" }` will take whatever is at `xxx`
 * in the traits, and move it to `yyy`. If `xxx` is a method of this facade,
 * it'll be called as a function instead of treated as a key into the traits.
 *
 * @example
 * var obj = { options: { traits: { foo: "bar" } }, anonymousId: "xxx" }
 * var facade = new Facade(obj)
 *
 * facade.traits() // { "foo": "bar" }
 * facade.traits({ "foo": "asdf" }) // { "asdf": "bar" }
 * facade.traits({ "sessionId": "rofl" }) // { "rofl": "xxx" }
 *
 * @param {Object} aliases - A mapping from keys to the new keys they should be
 * transformed to.
 * @return {Object}
 */
Facade.prototype.traits = function(aliases) {
  var ret = this.proxy('options.traits') || {};
  var id = this.userId();
  aliases = aliases || {};

  if (id) ret.id = id;

  for (var alias in aliases) {
    var value = this[alias] == null ? this.proxy('options.traits.' + alias) : this[alias]();
    if (value == null) continue;
    ret[aliases[alias]] = value;
    delete ret[alias];
  }

  return ret;
};

/**
 * The library and version of the client used to produce the message.
 *
 * If the library name cannot be determined, it is set to `"unknown"`. If the
 * version cannot be determined, it is set to `null`.
 *
 * @return {{name: string, version: string}}
 */
Facade.prototype.library = function() {
  var library = this.proxy('options.library');
  if (!library) return { name: 'unknown', version: null };
  if (typeof library === 'string') return { name: library, version: null };
  return library;
};

/**
 * Return the device information, falling back to an empty object.
 *
 * Interesting values of `type` are `"ios"` and `"android"`, but other values
 * are possible if the client is doing something unusual with `context.device`.
 *
 * @return {{type: string}}
 */
Facade.prototype.device = function() {
  var device = this.proxy('context.device');
  if (type(device) !== 'object') device = {};
  var library = this.library().name;
  if (device.type) return device;

  if (library.indexOf('ios') > -1) device.type = 'ios';
  if (library.indexOf('android') > -1) device.type = 'android';
  return device;
};

/**
 * Get the User-Agent from `context.userAgent`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return string
 */
Facade.prototype.userAgent = Facade.proxy('context.userAgent');

/**
 * Get the timezone from `context.timezone`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return string
 */
Facade.prototype.timezone = Facade.proxy('context.timezone');

/**
 * Get the timestamp from `context.timestamp`.
 *
 * @function
 * @return string
 */
Facade.prototype.timestamp = Facade.field('timestamp');

/**
 * Get the channel from `channel`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return string
 */
Facade.prototype.channel = Facade.field('channel');

/**
 * Get the IP address from `context.ip`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return string
 */
Facade.prototype.ip = Facade.proxy('context.ip');

/**
 * Get the user ID from `userId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return string
 */
Facade.prototype.userId = Facade.field('userId');

/**
 * Get the ZIP/Postal code from `traits`, `traits.address`, `properties`, or
 * `properties.address`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @name zip
 * @function
 * @memberof Facade.prototype
 * @return {string}
 */

/**
 * Get the country from `traits`, `traits.address`, `properties`, or
 * `properties.address`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @name country
 * @function
 * @memberof Facade.prototype
 * @return {string}
 */

/**
 * Get the street from `traits`, `traits.address`, `properties`, or
 * `properties.address`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @name street
 * @function
 * @memberof Facade.prototype
 * @return {string}
 */

/**
 * Get the state from `traits`, `traits.address`, `properties`, or
 * `properties.address`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @name state
 * @function
 * @memberof Facade.prototype
 * @return {string}
 */

/**
 * Get the city from `traits`, `traits.address`, `properties`, or
 * `properties.address`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @name city
 * @function
 * @memberof Facade.prototype
 * @return {string}
 */

/**
 * Get the region from `traits`, `traits.address`, `properties`, or
 * `properties.address`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @name region
 * @function
 * @memberof Facade.prototype
 * @return {string}
 */

address(Facade.prototype);

/**
 * Return the cloned and traversed object
 *
 * @ignore
 * @param {*} obj
 * @return {*}
 */
function transform(obj) {
  return clone(obj);
}

module.exports = Facade;

},{"./address":80,"./is-enabled":87,"./utils":91,"@segment/isodate-traverse":36,"new-date":72,"obj-case":77}],84:[function(require,module,exports){
'use strict';

var inherit = require('./utils').inherit;
var isEmail = require('is-email');
var newDate = require('new-date');
var Facade = require('./facade');

/**
 * Initialize a new `Group` facade with a `dictionary` of arguments.
 *
 * @param {Object} dictionary - The object to wrap.
 * @param {string} [dictionary.userId] - The user to add to the group.
 * @param {string} [dictionary.groupId] - The ID of the group.
 * @param {Object} [dictionary.traits] - The traits of the group.
 * @param {Object} opts - Options about what kind of Facade to create.
 *
 * @augments Facade
 */
function Group(dictionary, opts) {
  Facade.call(this, dictionary, opts);
}

inherit(Group, Facade);

/**
 * Return the type of facade this is. This will always return `"group"`.
 *
 * @return {string}
 */
Group.prototype.action = function() {
  return 'group';
};

/**
 * An alias for {@link Group#action}.
 *
 * @function
 * @return {string}
 */
Group.prototype.type = Group.prototype.action;

/**
 * Get the group ID from `groupId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Group.prototype.groupId = Facade.field('groupId');

/**
 * Get the time of creation of the group from `traits.createdAt`,
 * `traits.created`, `properties.createdAt`, or `properties.created`.
 *
 * @return {Date}
 */
Group.prototype.created = function() {
  var created = this.proxy('traits.createdAt')
    || this.proxy('traits.created')
    || this.proxy('properties.createdAt')
    || this.proxy('properties.created');

  if (created) return newDate(created);
};

/**
 * Get the group's email from `traits.email`, falling back to `groupId` only if
 * it looks like a valid email.
 *
 * @return {string}
 */
Group.prototype.email = function() {
  var email = this.proxy('traits.email');
  if (email) return email;
  var groupId = this.groupId();
  if (isEmail(groupId)) return groupId;
};

/**
 * Get the group's traits. This is identical to how {@link Facade#traits}
 * works, except it looks at `traits.*` instead of `options.traits.*`.
 *
 * Traits are gotten from `traits`, augmented with a property `id` with
 * the event's `groupId`.
 *
 * The parameter `aliases` is meant to transform keys in `traits` into new
 * keys. Each alias like `{ "xxx": "yyy" }` will take whatever is at `xxx` in
 * the traits, and move it to `yyy`. If `xxx` is a method of this facade, it'll
 * be called as a function instead of treated as a key into the traits.
 *
 * @example
 * var obj = { traits: { foo: "bar" }, anonymousId: "xxx" }
 * var group = new Group(obj)
 *
 * group.traits() // { "foo": "bar" }
 * group.traits({ "foo": "asdf" }) // { "asdf": "bar" }
 * group.traits({ "sessionId": "rofl" }) // { "rofl": "xxx" }
 *
 * @param {Object} aliases - A mapping from keys to the new keys they should be
 * transformed to.
 * @return {Object}
 */
Group.prototype.traits = function(aliases) {
  var ret = this.properties();
  var id = this.groupId();
  aliases = aliases || {};

  if (id) ret.id = id;

  for (var alias in aliases) {
    var value = this[alias] == null ? this.proxy('traits.' + alias) : this[alias]();
    if (value == null) continue;
    ret[aliases[alias]] = value;
    delete ret[alias];
  }

  return ret;
};

/**
 * Get the group's name from `traits.name`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Group.prototype.name = Facade.proxy('traits.name');

/**
 * Get the group's industry from `traits.industry`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Group.prototype.industry = Facade.proxy('traits.industry');

/**
 * Get the group's employee count from `traits.employees`.
 *
 * This *should* be a number, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {number}
 */
Group.prototype.employees = Facade.proxy('traits.employees');

/**
 * Get the group's properties from `traits` or `properties`, falling back to
 * simply an empty object.
 *
 * @return {Object}
 */
Group.prototype.properties = function() {
  // TODO remove this function
  return this.field('traits') || this.field('properties') || {};
};

module.exports = Group;

},{"./facade":83,"./utils":91,"is-email":65,"new-date":72}],85:[function(require,module,exports){
'use strict';

var Facade = require('./facade');
var get = require('obj-case');
var inherit = require('./utils').inherit;
var isEmail = require('is-email');
var newDate = require('new-date');
var trim = require('trim');
var type = require('./utils').type;

/**
 * Initialize a new `Identify` facade with a `dictionary` of arguments.
 *
 * @param {Object} dictionary - The object to wrap.
 * @param {string} [dictionary.userId] - The ID of the user.
 * @param {string} [dictionary.anonymousId] - The anonymous ID of the user.
 * @param {string} [dictionary.traits] - The user's traits.
 * @param {Object} opts - Options about what kind of Facade to create.
 *
 * @augments Facade
 */
function Identify(dictionary, opts) {
  Facade.call(this, dictionary, opts);
}

inherit(Identify, Facade);

/**
 * Return the type of facade this is. This will always return `"identify"`.
 *
 * @return {string}
 */
Identify.prototype.action = function() {
  return 'identify';
};

/**
 * An alias for {@link Identify#action}.
 *
 * @function
 * @return {string}
 */
Identify.prototype.type = Identify.prototype.action;

/**
 * Get the user's traits. This is identical to how {@link Facade#traits} works,
 * except it looks at `traits.*` instead of `options.traits.*`.
 *
 * Traits are gotten from `traits`, augmented with a property `id` with
 * the event's `userId`.
 *
 * The parameter `aliases` is meant to transform keys in `traits` into new
 * keys. Each alias like `{ "xxx": "yyy" }` will take whatever is at `xxx` in
 * the traits, and move it to `yyy`. If `xxx` is a method of this facade, it'll
 * be called as a function instead of treated as a key into the traits.
 *
 * @example
 * var obj = { traits: { foo: "bar" }, anonymousId: "xxx" }
 * var identify = new Identify(obj)
 *
 * identify.traits() // { "foo": "bar" }
 * identify.traits({ "foo": "asdf" }) // { "asdf": "bar" }
 * identify.traits({ "sessionId": "rofl" }) // { "rofl": "xxx" }
 *
 * @param {Object} aliases - A mapping from keys to the new keys they should be
 * transformed to.
 * @return {Object}
 */
Identify.prototype.traits = function(aliases) {
  var ret = this.field('traits') || {};
  var id = this.userId();
  aliases = aliases || {};

  if (id) ret.id = id;

  for (var alias in aliases) {
    var value = this[alias] == null ? this.proxy('traits.' + alias) : this[alias]();
    if (value == null) continue;
    ret[aliases[alias]] = value;
    if (alias !== aliases[alias]) delete ret[alias];
  }

  return ret;
};

/**
 * Get the user's email from `traits.email`, falling back to `userId` only if
 * it looks like a valid email.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Identify.prototype.email = function() {
  var email = this.proxy('traits.email');
  if (email) return email;

  var userId = this.userId();
  if (isEmail(userId)) return userId;
};

/**
 * Get the time of creation of the user from `traits.created` or
 * `traits.createdAt`.
 *
 * @return {Date}
 */
Identify.prototype.created = function() {
  var created = this.proxy('traits.created') || this.proxy('traits.createdAt');
  if (created) return newDate(created);
};

/**
 * Get the time of creation of the user's company from `traits.company.created`
 * or `traits.company.createdAt`.
 *
 * @return {Date}
 */
Identify.prototype.companyCreated = function() {
  var created = this.proxy('traits.company.created') || this.proxy('traits.company.createdAt');

  if (created) {
    return newDate(created);
  }
};

/**
 * Get the user's company name from `traits.company.name`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Identify.prototype.companyName = function() {
  return this.proxy('traits.company.name');
};

/**
 * Get the user's name `traits.name`, falling back to combining {@link
 * Identify#firstName} and {@link Identify#lastName} if possible.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Identify.prototype.name = function() {
  var name = this.proxy('traits.name');
  if (typeof name === 'string') {
    return trim(name);
  }

  var firstName = this.firstName();
  var lastName = this.lastName();
  if (firstName && lastName) {
    return trim(firstName + ' ' + lastName);
  }
};

/**
 * Get the user's first name from `traits.firstName`, optionally splitting it
 * out of a the full name if that's all that was provided.
 *
 * Splitting the full name works on the assumption that the full name is of the
 * form "FirstName LastName"; it will not work for non-Western names.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Identify.prototype.firstName = function() {
  var firstName = this.proxy('traits.firstName');
  if (typeof firstName === 'string') {
    return trim(firstName);
  }

  var name = this.proxy('traits.name');
  if (typeof name === 'string') {
    return trim(name).split(' ')[0];
  }
};

/**
 * Get the user's last name from `traits.lastName`, optionally splitting it out
 * of a the full name if that's all that was provided.
 *
 * Splitting the full name works on the assumption that the full name is of the
 * form "FirstName LastName"; it will not work for non-Western names.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Identify.prototype.lastName = function() {
  var lastName = this.proxy('traits.lastName');
  if (typeof lastName === 'string') {
    return trim(lastName);
  }

  var name = this.proxy('traits.name');
  if (typeof name !== 'string') {
    return;
  }

  var space = trim(name).indexOf(' ');
  if (space === -1) {
    return;
  }

  return trim(name.substr(space + 1));
};

/**
 * Get the user's "unique id" from `userId`, `traits.username`, or
 * `traits.email`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Identify.prototype.uid = function() {
  return this.userId() || this.username() || this.email();
};

/**
 * Get the user's description from `traits.description` or `traits.background`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Identify.prototype.description = function() {
  return this.proxy('traits.description') || this.proxy('traits.background');
};

/**
 * Get the user's age from `traits.age`, falling back to computing it from
 * `traits.birthday` and the current time.
 *
 * @return {number}
 */
Identify.prototype.age = function() {
  var date = this.birthday();
  var age = get(this.traits(), 'age');
  if (age != null) return age;
  if (type(date) !== 'date') return;
  var now = new Date();
  return now.getFullYear() - date.getFullYear();
};

/**
 * Get the URL of the user's avatar from `traits.avatar`, `traits.photoUrl`, or
 * `traits.avatarUrl`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Identify.prototype.avatar = function() {
  var traits = this.traits();
  return get(traits, 'avatar') || get(traits, 'photoUrl') || get(traits, 'avatarUrl');
};

/**
 * Get the user's job position from `traits.position` or `traits.jobTitle`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Identify.prototype.position = function() {
  var traits = this.traits();
  return get(traits, 'position') || get(traits, 'jobTitle');
};

/**
 * Get the user's username from `traits.username`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Identify.prototype.username = Facade.proxy('traits.username');

/**
 * Get the user's website from `traits.website`, or if there are multiple in
 * `traits.websites`, return the first one.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Identify.prototype.website = Facade.one('traits.website');

/**
 * Get the user's websites from `traits.websites`, or if there is only one in
 * `traits.website`, then wrap it in an array.
 *
 * This *should* be an array of strings, but may not be if the client isn't
 * adhering to the spec.
 *
 * @function
 * @return {array}
 */
Identify.prototype.websites = Facade.multi('traits.website');

/**
 * Get the user's phone number from `traits.phone`, or if there are multiple in
 * `traits.phones`, return the first one.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Identify.prototype.phone = Facade.one('traits.phone');

/**
 * Get the user's phone numbers from `traits.phones`, or if there is only one
 * in `traits.phone`, then wrap it in an array.
 *
 * This *should* be an array of strings, but may not be if the client isn't
 * adhering to the spec.
 *
 * @function
 * @return {array}
 */
Identify.prototype.phones = Facade.multi('traits.phone');

/**
 * Get the user's address from `traits.address`.
 *
 * This *should* be an object, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {object}
 */
Identify.prototype.address = Facade.proxy('traits.address');

/**
 * Get the user's gender from `traits.gender`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Identify.prototype.gender = Facade.proxy('traits.gender');

/**
 * Get the user's birthday from `traits.birthday`.
 *
 * This *should* be a Date if `opts.traverse` was enabled (it is by default)
 * when constructing this Identify. Otherwise, it should be a string. But it
 * may be neither if the client isn't adhering to the spec.
 * spec.
 *
 * @function
 * @return {object}
 */
Identify.prototype.birthday = Facade.proxy('traits.birthday');

module.exports = Identify;

},{"./facade":83,"./utils":91,"is-email":65,"new-date":72,"obj-case":77,"trim":97}],86:[function(require,module,exports){
'use strict';

var Facade = require('./facade');

Facade.Alias = require('./alias');
Facade.Group = require('./group');
Facade.Identify = require('./identify');
Facade.Track = require('./track');
Facade.Page = require('./page');
Facade.Screen = require('./screen');
Facade.Delete = require('./delete');

module.exports = Facade;

},{"./alias":81,"./delete":82,"./facade":83,"./group":84,"./identify":85,"./page":88,"./screen":89,"./track":90}],87:[function(require,module,exports){
'use strict';

// A few integrations are disabled by default. They must be explicitly enabled
// by setting options[Provider] = true.
var disabled = {
  Salesforce: true
};

/**
 * Check whether an integration should be enabled by default.
 *
 * @ignore
 * @param {string} integration
 * @return {boolean}
 */
module.exports = function(integration) {
  return !disabled[integration];
};

},{}],88:[function(require,module,exports){
'use strict';

var inherit = require('./utils').inherit;
var Facade = require('./facade');
var Track = require('./track');
var isEmail = require('is-email');

/**
 * Initialize a new `Page` facade with a `dictionary` of arguments.
 *
 * @param {Object} dictionary - The object to wrap.
 * @param {string} [dictionary.category] - The page category.
 * @param {string} [dictionary.name] - The page name.
 * @param {string} [dictionary.properties] - The page properties.
 * @param {Object} opts - Options about what kind of Facade to create.
 *
 * @augments Facade
 */
function Page(dictionary, opts) {
  Facade.call(this, dictionary, opts);
}

inherit(Page, Facade);

/**
 * Return the type of facade this is. This will always return `"page"`.
 *
 * @return {string}
 */
Page.prototype.action = function() {
  return 'page';
};

/**
 * An alias for {@link Page#action}.
 *
 * @function
 * @return {string}
 */
Page.prototype.type = Page.prototype.action;

/**
 * Get the page category from `category`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Page.prototype.category = Facade.field('category');

/**
 * Get the page name from `name`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Page.prototype.name = Facade.field('name');

/**
 * Get the page title from `properties.title`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Page.prototype.title = Facade.proxy('properties.title');

/**
 * Get the page path from `properties.path`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Page.prototype.path = Facade.proxy('properties.path');

/**
 * Get the page URL from `properties.url`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Page.prototype.url = Facade.proxy('properties.url');

/**
 * Get the HTTP referrer from `context.referrer.url`, `context.page.referrer`,
 * or `properties.referrer`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Page.prototype.referrer = function() {
  return this.proxy('context.referrer.url')
    || this.proxy('context.page.referrer')
    || this.proxy('properties.referrer');
};

/**
 * Get the page's properties. This is identical to how {@link Facade#traits}
 * works, except it looks at `properties.*` instead of `options.traits.*`.
 *
 * Properties are gotten from `properties`, augmented with the page's `name`
 * and `category`.
 *
 * The parameter `aliases` is meant to transform keys in `properties` into new
 * keys. Each alias like `{ "xxx": "yyy" }` will take whatever is at `xxx` in
 * the traits, and move it to `yyy`. If `xxx` is a method of this facade, it'll
 * be called as a function instead of treated as a key into the traits.
 *
 * @example
 * var obj = { properties: { foo: "bar" }, anonymousId: "xxx" }
 * var page = new Page(obj)
 *
 * page.traits() // { "foo": "bar" }
 * page.traits({ "foo": "asdf" }) // { "asdf": "bar" }
 * page.traits({ "sessionId": "rofl" }) // { "rofl": "xxx" }
 *
 * @param {Object} aliases - A mapping from keys to the new keys they should be
 * transformed to.
 * @return {Object}
 */
Page.prototype.properties = function(aliases) {
  var props = this.field('properties') || {};
  var category = this.category();
  var name = this.name();
  aliases = aliases || {};

  if (category) props.category = category;
  if (name) props.name = name;

  for (var alias in aliases) {
    var value = this[alias] == null
      ? this.proxy('properties.' + alias)
      : this[alias]();
    if (value == null) continue;
    props[aliases[alias]] = value;
    if (alias !== aliases[alias]) delete props[alias];
  }

  return props;
};

/**
 * Get the user's email from `context.traits.email` or `properties.email`,
 * falling back to `userId` if it's a valid email.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Page.prototype.email = function() {
  var email = this.proxy('context.traits.email') || this.proxy('properties.email');
  if (email) return email;

  var userId = this.userId();
  if (isEmail(userId)) return userId;
};

/**
 * Get the page fullName. This is `$category $name` if both are present, and
 * just `name` otherwiser.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Page.prototype.fullName = function() {
  var category = this.category();
  var name = this.name();
  return name && category
    ? category + ' ' + name
    : name;
};

/**
 * Get an event name from this page call. If `name` is present, this will be
 * `Viewed $name Page`; otherwise, it will be `Loaded a Page`.
 *
 * @param {string} name - The name of this page.
 * @return {string}
 */
Page.prototype.event = function(name) {
  return name
    ? 'Viewed ' + name + ' Page'
    : 'Loaded a Page';
};

/**
 * Convert this Page to a {@link Track} facade. The inputted `name` will be
 * converted to the Track's event name via {@link Page#event}.
 *
 * @param {string} name
 * @return {Track}
 */
Page.prototype.track = function(name) {
  var json = this.json();
  json.event = this.event(name);
  json.timestamp = this.timestamp();
  json.properties = this.properties();
  return new Track(json, this.opts);
};

module.exports = Page;

},{"./facade":83,"./track":90,"./utils":91,"is-email":65}],89:[function(require,module,exports){
'use strict';

var inherit = require('./utils').inherit;
var Page = require('./page');
var Track = require('./track');

/**
 * Initialize a new `Screen` facade with a `dictionary` of arguments.
 *
 * Note that this class extends {@link Page}, so its methods are available to
 * instances of this class as well.
 *
 * @param {Object} dictionary - The object to wrap.
 * @param {string} [dictionary.category] - The page category.
 * @param {string} [dictionary.name] - The page name.
 * @param {string} [dictionary.properties] - The page properties.
 * @param {Object} opts - Options about what kind of Facade to create.
 *
 * @augments Page
 */
function Screen(dictionary, opts) {
  Page.call(this, dictionary, opts);
}

inherit(Screen, Page);

/**
 * Return the type of facade this is. This will always return `"screen"`.
 *
 * @return {string}
 */
Screen.prototype.action = function() {
  return 'screen';
};

/**
 * An alias for {@link Screen#action}.
 *
 * @function
 * @return {string}
 */
Screen.prototype.type = Screen.prototype.action;

/**
 * Get an event name from this screen call. If `name` is present, this will be
 * `Viewed $name Screen`; otherwise, it will be `Loaded a Screen`.
 *
 * @param {string} name - The name of this screen.
 * @return {string}
 */
Screen.prototype.event = function(name) {
  return name ? 'Viewed ' + name + ' Screen' : 'Loaded a Screen';
};

/**
 * Convert this Screen to a {@link Track} facade. The inputted `name` will be
 * converted to the Track's event name via {@link Screen#event}.
 *
 * @param {string} name
 * @return {Track}
 */
Screen.prototype.track = function(name) {
  var json = this.json();
  json.event = this.event(name);
  json.timestamp = this.timestamp();
  json.properties = this.properties();
  return new Track(json, this.opts);
};

module.exports = Screen;

},{"./page":88,"./track":90,"./utils":91}],90:[function(require,module,exports){
'use strict';

var inherit = require('./utils').inherit;
var type = require('./utils').type;
var Facade = require('./facade');
var Identify = require('./identify');
var isEmail = require('is-email');
var get = require('obj-case');

/**
 * Initialize a new `Track` facade with a `dictionary` of arguments.
 *
 * @param {Object} dictionary - The object to wrap.
 * @param {string} [dictionary.event] - The name of the event being tracked.
 * @param {string} [dictionary.userId] - The ID of the user being tracked.
 * @param {string} [dictionary.anonymousId] - The anonymous ID of the user.
 * @param {string} [dictionary.properties] - Properties of the track event.
 * @param {Object} opts - Options about what kind of Facade to create.
 *
 * @augments Facade
 */
function Track(dictionary, opts) {
  Facade.call(this, dictionary, opts);
}

inherit(Track, Facade);

/**
 * Return the type of facade this is. This will always return `"track"`.
 *
 * @return {string}
 */
Track.prototype.action = function() {
  return 'track';
};

/**
 * An alias for {@link Track#action}.
 *
 * @function
 * @return {string}
 */
Track.prototype.type = Track.prototype.action;

/**
 * Get the event name from `event`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Track.prototype.event = Facade.field('event');

/**
 * Get the event value, usually the monetary value, from `properties.value`.
 *
 * This *should* be a number, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Track.prototype.value = Facade.proxy('properties.value');

/**
 * Get the event cateogry from `properties.category`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Track.prototype.category = Facade.proxy('properties.category');

/**
 * Get the event ID from `properties.id`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Track.prototype.id = Facade.proxy('properties.id');

/**
 * Get the product ID from `properties.productId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.productId = function() {
  return this.proxy('properties.product_id') || this.proxy('properties.productId');
};

/**
 * Get the promotion ID from `properties.promotionId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.promotionId = function() {
  return this.proxy('properties.promotion_id') || this.proxy('properties.promotionId');
};

/**
 * Get the cart ID from `properties.cartId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.cartId = function() {
  return this.proxy('properties.cart_id') || this.proxy('properties.cartId');
};

/**
 * Get the checkout ID from `properties.checkoutId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.checkoutId = function() {
  return this.proxy('properties.checkout_id') || this.proxy('properties.checkoutId');
};

/**
 * Get the payment ID from `properties.paymentId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.paymentId = function() {
  return this.proxy('properties.payment_id') || this.proxy('properties.paymentId');
};

/**
 * Get the coupon ID from `properties.couponId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.couponId = function() {
  return this.proxy('properties.coupon_id') || this.proxy('properties.couponId');
};

/**
 * Get the wishlist ID from `properties.wishlistId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.wishlistId = function() {
  return this.proxy('properties.wishlist_id') || this.proxy('properties.wishlistId');
};

/**
 * Get the review ID from `properties.reviewId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.reviewId = function() {
  return this.proxy('properties.review_id') || this.proxy('properties.reviewId');
};

/**
 * Get the order ID from `properties.id` or `properties.orderId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.orderId = function() {
  // doesn't follow above convention since this fallback order was how it used to be
  return this.proxy('properties.id')
    || this.proxy('properties.order_id')
    || this.proxy('properties.orderId');
};

/**
 * Get the SKU from `properties.sku`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Track.prototype.sku = Facade.proxy('properties.sku');

/**
 * Get the amount of tax for this purchase from `properties.tax`.
 *
 * This *should* be a number, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {number}
 */
Track.prototype.tax = Facade.proxy('properties.tax');

/**
 * Get the name of this event from `properties.name`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Track.prototype.name = Facade.proxy('properties.name');

/**
 * Get the price of this purchase from `properties.price`.
 *
 * This *should* be a number, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {number}
 */
Track.prototype.price = Facade.proxy('properties.price');

/**
 * Get the total for this purchase from `properties.total`.
 *
 * This *should* be a number, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {number}
 */
Track.prototype.total = Facade.proxy('properties.total');

/**
 * Whether this is a repeat purchase from `properties.repeat`.
 *
 * This *should* be a boolean, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {boolean}
 */
Track.prototype.repeat = Facade.proxy('properties.repeat');

/**
 * Get the coupon for this purchase from `properties.coupon`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Track.prototype.coupon = Facade.proxy('properties.coupon');

/**
 * Get the shipping for this purchase from `properties.shipping`.
 *
 * This *should* be a number, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {number}
 */
Track.prototype.shipping = Facade.proxy('properties.shipping');

/**
 * Get the discount for this purchase from `properties.discount`.
 *
 * This *should* be a number, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {number}
 */
Track.prototype.discount = Facade.proxy('properties.discount');

/**
 * Get the shipping method for this purchase from `properties.shippingMethod`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.shippingMethod = function() {
  return this.proxy('properties.shipping_method') || this.proxy('properties.shippingMethod');
};

/**
 * Get the payment method for this purchase from `properties.paymentMethod`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.paymentMethod = function() {
  return this.proxy('properties.payment_method') || this.proxy('properties.paymentMethod');
};

/**
 * Get a description for this event from `properties.description`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Track.prototype.description = Facade.proxy('properties.description');

/**
 * Get a plan, as in the plan the user is on, for this event from
 * `properties.plan`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string}
 */
Track.prototype.plan = Facade.proxy('properties.plan');

/**
 * Get the subtotal for this purchase from `properties.subtotal`.
 *
 * If `properties.subtotal` isn't available, then fall back to computing the
 * total from `properties.total` or `properties.revenue`, and then subtracting
 * tax, shipping, and discounts.
 *
 * If neither subtotal, total, nor revenue are available, then return 0.
 *
 * @return {number}
 */
Track.prototype.subtotal = function() {
  var subtotal = get(this.properties(), 'subtotal');
  var total = this.total() || this.revenue();

  if (subtotal) return subtotal;
  if (!total) return 0;

  if (this.total()) {
    var n = this.tax();
    if (n) total -= n;
    n = this.shipping();
    if (n) total -= n;
    n = this.discount();
    if (n) total += n;
  }

  return total;
};

/**
 * Get the products for this event from `properties.products` if it's an
 * array, falling back to an empty array.
 *
 * @return {Array}
 */
Track.prototype.products = function() {
  var props = this.properties();
  var products = get(props, 'products');
  return type(products) === 'array' ? products : [];
};

/**
 * Get the quantity for this event from `properties.quantity`, falling back to
 * a quantity of one.
 *
 * @return {number}
 */
Track.prototype.quantity = function() {
  var props = this.obj.properties || {};
  return props.quantity || 1;
};

/**
 * Get the currency for this event from `properties.currency`, falling back to
 * "USD".
 *
 * @return {string}
 */
Track.prototype.currency = function() {
  var props = this.obj.properties || {};
  return props.currency || 'USD';
};

/**
 * Get the referrer for this event from `context.referrer.url`,
 * `context.page.referrer`, or `properties.referrer`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string}
 */
Track.prototype.referrer = function() {
  // TODO re-examine whether this function is necessary
  return this.proxy('context.referrer.url')
    || this.proxy('context.page.referrer')
    || this.proxy('properties.referrer');
};

/**
 * Get the query for this event from `options.query`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @function
 * @return {string|object}
 */
Track.prototype.query = Facade.proxy('options.query');

/**
 * Get the page's properties. This is identical to how {@link Facade#traits}
 * works, except it looks at `properties.*` instead of `options.traits.*`.
 *
 * Properties are gotten from `properties`.
 *
 * The parameter `aliases` is meant to transform keys in `properties` into new
 * keys. Each alias like `{ "xxx": "yyy" }` will take whatever is at `xxx` in
 * the traits, and move it to `yyy`. If `xxx` is a method of this facade, it'll
 * be called as a function instead of treated as a key into the traits.
 *
 * @example
 * var obj = { properties: { foo: "bar" }, anonymousId: "xxx" }
 * var track = new Track(obj)
 *
 * track.traits() // { "foo": "bar" }
 * track.traits({ "foo": "asdf" }) // { "asdf": "bar" }
 * track.traits({ "sessionId": "rofl" }) // { "rofl": "xxx" }
 *
 * @param {Object} aliases - A mapping from keys to the new keys they should be
 * transformed to.
 * @return {Object}
 */
Track.prototype.properties = function(aliases) {
  var ret = this.field('properties') || {};
  aliases = aliases || {};

  for (var alias in aliases) {
    var value = this[alias] == null ? this.proxy('properties.' + alias) : this[alias]();
    if (value == null) continue;
    ret[aliases[alias]] = value;
    delete ret[alias];
  }

  return ret;
};

/**
 * Get the username of the user for this event from `traits.username`,
 * `properties.username`, `userId`, or `anonymousId`.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string|undefined}
 */
Track.prototype.username = function() {
  return this.proxy('traits.username')
    || this.proxy('properties.username')
    || this.userId()
    || this.sessionId();
};

/**
 * Get the email of the user for this event from `trais.email`,
 * `properties.email`, or `options.traits.email`, falling back to `userId` if
 * it looks like a valid email.
 *
 * This *should* be a string, but may not be if the client isn't adhering to
 * the spec.
 *
 * @return {string|undefined}
 */
Track.prototype.email = function() {
  var email = this.proxy('traits.email')
    || this.proxy('properties.email')
    || this.proxy('options.traits.email');
  if (email) return email;

  var userId = this.userId();
  if (isEmail(userId)) return userId;
};

/**
 * Get the revenue for this event.
 *
 * If this is an "Order Completed" event, this will be the `properties.total`
 * falling back to the `properties.revenue`. For all other events, this is
 * simply taken from `properties.revenue`.
 *
 * If there are dollar signs in these properties, they will be removed. The
 * result will be parsed into a number.
 *
 * @return {number}
 */
Track.prototype.revenue = function() {
  var revenue = this.proxy('properties.revenue');
  var event = this.event();
  var orderCompletedRegExp = /^[ _]?completed[ _]?order[ _]?|^[ _]?order[ _]?completed[ _]?$/i;

  // it's always revenue, unless it's called during an order completion.
  if (!revenue && event && event.match(orderCompletedRegExp)) {
    revenue = this.proxy('properties.total');
  }

  return currency(revenue);
};

/**
 * Get the revenue for this event in "cents" -- in other words, multiply the
 * {@link Track#revenue} by 100, or return 0 if there isn't a numerical revenue
 * for this event.
 *
 * @return {number}
 */
Track.prototype.cents = function() {
  var revenue = this.revenue();
  return typeof revenue !== 'number' ? this.value() || 0 : revenue * 100;
};

/**
 * Convert this event into an {@link Identify} facade.
 *
 * This works by taking this event's underlying object and creating an Identify
 * from it. This event's traits, taken from `options.traits`, will be used as
 * the Identify's traits.
 *
 * @return {Identify}
 */
Track.prototype.identify = function() {
  // TODO: remove me.
  var json = this.json();
  json.traits = this.traits();
  return new Identify(json, this.opts);
};

/**
 * Get float from currency value.
 *
 * @ignore
 * @param {*} val
 * @return {number}
 */
function currency(val) {
  if (!val) return;
  if (typeof val === 'number') {
    return val;
  }
  if (typeof val !== 'string') {
    return;
  }

  val = val.replace(/\$/g, '');
  val = parseFloat(val);

  if (!isNaN(val)) {
    return val;
  }
}

module.exports = Track;

},{"./facade":83,"./identify":85,"./utils":91,"is-email":65,"obj-case":77}],91:[function(require,module,exports){
'use strict';

exports.inherit = require('inherits');
exports.clone = require('@ndhoule/clone');
exports.type = require('type-component');

},{"@ndhoule/clone":3,"inherits":64,"type-component":98}],92:[function(require,module,exports){

/**
 * Generate a slug from the given `str`.
 *
 * example:
 *
 *        generate('foo bar');
 *        // > foo-bar
 *
 * @param {String} str
 * @param {Object} options
 * @config {String|RegExp} [replace] characters to replace, defaulted to `/[^a-z0-9]/g`
 * @config {String} [separator] separator to insert, defaulted to `-`
 * @return {String}
 */

module.exports = function (str, options) {
  options || (options = {});
  return str.toLowerCase()
    .replace(options.replace || /[^a-z0-9]/g, ' ')
    .replace(/^ +| +$/g, '')
    .replace(/ +/g, options.separator || '-')
};

},{}],93:[function(require,module,exports){
(function (factory) {
    if (typeof exports === 'object') {
        // Node/CommonJS
        module.exports = factory();
    } else if (typeof define === 'function' && define.amd) {
        // AMD
        define(factory);
    } else {
        // Browser globals (with support for web workers)
        var glob;

        try {
            glob = window;
        } catch (e) {
            glob = self;
        }

        glob.SparkMD5 = factory();
    }
}(function (undefined) {

    'use strict';

    /*
     * Fastest md5 implementation around (JKM md5).
     * Credits: Joseph Myers
     *
     * @see http://www.myersdaily.org/joseph/javascript/md5-text.html
     * @see http://jsperf.com/md5-shootout/7
     */

    /* this function is much faster,
      so if possible we use it. Some IEs
      are the only ones I know of that
      need the idiotic second function,
      generated by an if clause.  */
    var add32 = function (a, b) {
        return (a + b) & 0xFFFFFFFF;
    },
        hex_chr = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'];


    function cmn(q, a, b, x, s, t) {
        a = add32(add32(a, q), add32(x, t));
        return add32((a << s) | (a >>> (32 - s)), b);
    }

    function ff(a, b, c, d, x, s, t) {
        return cmn((b & c) | ((~b) & d), a, b, x, s, t);
    }

    function gg(a, b, c, d, x, s, t) {
        return cmn((b & d) | (c & (~d)), a, b, x, s, t);
    }

    function hh(a, b, c, d, x, s, t) {
        return cmn(b ^ c ^ d, a, b, x, s, t);
    }

    function ii(a, b, c, d, x, s, t) {
        return cmn(c ^ (b | (~d)), a, b, x, s, t);
    }

    function md5cycle(x, k) {
        var a = x[0],
            b = x[1],
            c = x[2],
            d = x[3];

        a = ff(a, b, c, d, k[0], 7, -680876936);
        d = ff(d, a, b, c, k[1], 12, -389564586);
        c = ff(c, d, a, b, k[2], 17, 606105819);
        b = ff(b, c, d, a, k[3], 22, -1044525330);
        a = ff(a, b, c, d, k[4], 7, -176418897);
        d = ff(d, a, b, c, k[5], 12, 1200080426);
        c = ff(c, d, a, b, k[6], 17, -1473231341);
        b = ff(b, c, d, a, k[7], 22, -45705983);
        a = ff(a, b, c, d, k[8], 7, 1770035416);
        d = ff(d, a, b, c, k[9], 12, -1958414417);
        c = ff(c, d, a, b, k[10], 17, -42063);
        b = ff(b, c, d, a, k[11], 22, -1990404162);
        a = ff(a, b, c, d, k[12], 7, 1804603682);
        d = ff(d, a, b, c, k[13], 12, -40341101);
        c = ff(c, d, a, b, k[14], 17, -1502002290);
        b = ff(b, c, d, a, k[15], 22, 1236535329);

        a = gg(a, b, c, d, k[1], 5, -165796510);
        d = gg(d, a, b, c, k[6], 9, -1069501632);
        c = gg(c, d, a, b, k[11], 14, 643717713);
        b = gg(b, c, d, a, k[0], 20, -373897302);
        a = gg(a, b, c, d, k[5], 5, -701558691);
        d = gg(d, a, b, c, k[10], 9, 38016083);
        c = gg(c, d, a, b, k[15], 14, -660478335);
        b = gg(b, c, d, a, k[4], 20, -405537848);
        a = gg(a, b, c, d, k[9], 5, 568446438);
        d = gg(d, a, b, c, k[14], 9, -1019803690);
        c = gg(c, d, a, b, k[3], 14, -187363961);
        b = gg(b, c, d, a, k[8], 20, 1163531501);
        a = gg(a, b, c, d, k[13], 5, -1444681467);
        d = gg(d, a, b, c, k[2], 9, -51403784);
        c = gg(c, d, a, b, k[7], 14, 1735328473);
        b = gg(b, c, d, a, k[12], 20, -1926607734);

        a = hh(a, b, c, d, k[5], 4, -378558);
        d = hh(d, a, b, c, k[8], 11, -2022574463);
        c = hh(c, d, a, b, k[11], 16, 1839030562);
        b = hh(b, c, d, a, k[14], 23, -35309556);
        a = hh(a, b, c, d, k[1], 4, -1530992060);
        d = hh(d, a, b, c, k[4], 11, 1272893353);
        c = hh(c, d, a, b, k[7], 16, -155497632);
        b = hh(b, c, d, a, k[10], 23, -1094730640);
        a = hh(a, b, c, d, k[13], 4, 681279174);
        d = hh(d, a, b, c, k[0], 11, -358537222);
        c = hh(c, d, a, b, k[3], 16, -722521979);
        b = hh(b, c, d, a, k[6], 23, 76029189);
        a = hh(a, b, c, d, k[9], 4, -640364487);
        d = hh(d, a, b, c, k[12], 11, -421815835);
        c = hh(c, d, a, b, k[15], 16, 530742520);
        b = hh(b, c, d, a, k[2], 23, -995338651);

        a = ii(a, b, c, d, k[0], 6, -198630844);
        d = ii(d, a, b, c, k[7], 10, 1126891415);
        c = ii(c, d, a, b, k[14], 15, -1416354905);
        b = ii(b, c, d, a, k[5], 21, -57434055);
        a = ii(a, b, c, d, k[12], 6, 1700485571);
        d = ii(d, a, b, c, k[3], 10, -1894986606);
        c = ii(c, d, a, b, k[10], 15, -1051523);
        b = ii(b, c, d, a, k[1], 21, -2054922799);
        a = ii(a, b, c, d, k[8], 6, 1873313359);
        d = ii(d, a, b, c, k[15], 10, -30611744);
        c = ii(c, d, a, b, k[6], 15, -1560198380);
        b = ii(b, c, d, a, k[13], 21, 1309151649);
        a = ii(a, b, c, d, k[4], 6, -145523070);
        d = ii(d, a, b, c, k[11], 10, -1120210379);
        c = ii(c, d, a, b, k[2], 15, 718787259);
        b = ii(b, c, d, a, k[9], 21, -343485551);

        x[0] = add32(a, x[0]);
        x[1] = add32(b, x[1]);
        x[2] = add32(c, x[2]);
        x[3] = add32(d, x[3]);
    }

    function md5blk(s) {
        var md5blks = [],
            i; /* Andy King said do it this way. */

        for (i = 0; i < 64; i += 4) {
            md5blks[i >> 2] = s.charCodeAt(i) + (s.charCodeAt(i + 1) << 8) + (s.charCodeAt(i + 2) << 16) + (s.charCodeAt(i + 3) << 24);
        }
        return md5blks;
    }

    function md5blk_array(a) {
        var md5blks = [],
            i; /* Andy King said do it this way. */

        for (i = 0; i < 64; i += 4) {
            md5blks[i >> 2] = a[i] + (a[i + 1] << 8) + (a[i + 2] << 16) + (a[i + 3] << 24);
        }
        return md5blks;
    }

    function md51(s) {
        var n = s.length,
            state = [1732584193, -271733879, -1732584194, 271733878],
            i,
            length,
            tail,
            tmp,
            lo,
            hi;

        for (i = 64; i <= n; i += 64) {
            md5cycle(state, md5blk(s.substring(i - 64, i)));
        }
        s = s.substring(i - 64);
        length = s.length;
        tail = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
        for (i = 0; i < length; i += 1) {
            tail[i >> 2] |= s.charCodeAt(i) << ((i % 4) << 3);
        }
        tail[i >> 2] |= 0x80 << ((i % 4) << 3);
        if (i > 55) {
            md5cycle(state, tail);
            for (i = 0; i < 16; i += 1) {
                tail[i] = 0;
            }
        }

        // Beware that the final length might not fit in 32 bits so we take care of that
        tmp = n * 8;
        tmp = tmp.toString(16).match(/(.*?)(.{0,8})$/);
        lo = parseInt(tmp[2], 16);
        hi = parseInt(tmp[1], 16) || 0;

        tail[14] = lo;
        tail[15] = hi;

        md5cycle(state, tail);
        return state;
    }

    function md51_array(a) {
        var n = a.length,
            state = [1732584193, -271733879, -1732584194, 271733878],
            i,
            length,
            tail,
            tmp,
            lo,
            hi;

        for (i = 64; i <= n; i += 64) {
            md5cycle(state, md5blk_array(a.subarray(i - 64, i)));
        }

        // Not sure if it is a bug, however IE10 will always produce a sub array of length 1
        // containing the last element of the parent array if the sub array specified starts
        // beyond the length of the parent array - weird.
        // https://connect.microsoft.com/IE/feedback/details/771452/typed-array-subarray-issue
        a = (i - 64) < n ? a.subarray(i - 64) : new Uint8Array(0);

        length = a.length;
        tail = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
        for (i = 0; i < length; i += 1) {
            tail[i >> 2] |= a[i] << ((i % 4) << 3);
        }

        tail[i >> 2] |= 0x80 << ((i % 4) << 3);
        if (i > 55) {
            md5cycle(state, tail);
            for (i = 0; i < 16; i += 1) {
                tail[i] = 0;
            }
        }

        // Beware that the final length might not fit in 32 bits so we take care of that
        tmp = n * 8;
        tmp = tmp.toString(16).match(/(.*?)(.{0,8})$/);
        lo = parseInt(tmp[2], 16);
        hi = parseInt(tmp[1], 16) || 0;

        tail[14] = lo;
        tail[15] = hi;

        md5cycle(state, tail);

        return state;
    }

    function rhex(n) {
        var s = '',
            j;
        for (j = 0; j < 4; j += 1) {
            s += hex_chr[(n >> (j * 8 + 4)) & 0x0F] + hex_chr[(n >> (j * 8)) & 0x0F];
        }
        return s;
    }

    function hex(x) {
        var i;
        for (i = 0; i < x.length; i += 1) {
            x[i] = rhex(x[i]);
        }
        return x.join('');
    }

    // In some cases the fast add32 function cannot be used..
    if (hex(md51('hello')) !== '5d41402abc4b2a76b9719d911017c592') {
        add32 = function (x, y) {
            var lsw = (x & 0xFFFF) + (y & 0xFFFF),
                msw = (x >> 16) + (y >> 16) + (lsw >> 16);
            return (msw << 16) | (lsw & 0xFFFF);
        };
    }

    // ---------------------------------------------------

    /**
     * ArrayBuffer slice polyfill.
     *
     * @see https://github.com/ttaubert/node-arraybuffer-slice
     */

    if (typeof ArrayBuffer !== 'undefined' && !ArrayBuffer.prototype.slice) {
        (function () {
            function clamp(val, length) {
                val = (val | 0) || 0;

                if (val < 0) {
                    return Math.max(val + length, 0);
                }

                return Math.min(val, length);
            }

            ArrayBuffer.prototype.slice = function (from, to) {
                var length = this.byteLength,
                    begin = clamp(from, length),
                    end = length,
                    num,
                    target,
                    targetArray,
                    sourceArray;

                if (to !== undefined) {
                    end = clamp(to, length);
                }

                if (begin > end) {
                    return new ArrayBuffer(0);
                }

                num = end - begin;
                target = new ArrayBuffer(num);
                targetArray = new Uint8Array(target);

                sourceArray = new Uint8Array(this, begin, num);
                targetArray.set(sourceArray);

                return target;
            };
        })();
    }

    // ---------------------------------------------------

    /**
     * Helpers.
     */

    function toUtf8(str) {
        if (/[\u0080-\uFFFF]/.test(str)) {
            str = unescape(encodeURIComponent(str));
        }

        return str;
    }

    function utf8Str2ArrayBuffer(str, returnUInt8Array) {
        var length = str.length,
           buff = new ArrayBuffer(length),
           arr = new Uint8Array(buff),
           i;

        for (i = 0; i < length; i += 1) {
            arr[i] = str.charCodeAt(i);
        }

        return returnUInt8Array ? arr : buff;
    }

    function arrayBuffer2Utf8Str(buff) {
        return String.fromCharCode.apply(null, new Uint8Array(buff));
    }

    function concatenateArrayBuffers(first, second, returnUInt8Array) {
        var result = new Uint8Array(first.byteLength + second.byteLength);

        result.set(new Uint8Array(first));
        result.set(new Uint8Array(second), first.byteLength);

        return returnUInt8Array ? result : result.buffer;
    }

    function hexToBinaryString(hex) {
        var bytes = [],
            length = hex.length,
            x;

        for (x = 0; x < length - 1; x += 2) {
            bytes.push(parseInt(hex.substr(x, 2), 16));
        }

        return String.fromCharCode.apply(String, bytes);
    }

    // ---------------------------------------------------

    /**
     * SparkMD5 OOP implementation.
     *
     * Use this class to perform an incremental md5, otherwise use the
     * static methods instead.
     */

    function SparkMD5() {
        // call reset to init the instance
        this.reset();
    }

    /**
     * Appends a string.
     * A conversion will be applied if an utf8 string is detected.
     *
     * @param {String} str The string to be appended
     *
     * @return {SparkMD5} The instance itself
     */
    SparkMD5.prototype.append = function (str) {
        // Converts the string to utf8 bytes if necessary
        // Then append as binary
        this.appendBinary(toUtf8(str));

        return this;
    };

    /**
     * Appends a binary string.
     *
     * @param {String} contents The binary string to be appended
     *
     * @return {SparkMD5} The instance itself
     */
    SparkMD5.prototype.appendBinary = function (contents) {
        this._buff += contents;
        this._length += contents.length;

        var length = this._buff.length,
            i;

        for (i = 64; i <= length; i += 64) {
            md5cycle(this._hash, md5blk(this._buff.substring(i - 64, i)));
        }

        this._buff = this._buff.substring(i - 64);

        return this;
    };

    /**
     * Finishes the incremental computation, reseting the internal state and
     * returning the result.
     *
     * @param {Boolean} raw True to get the raw string, false to get the hex string
     *
     * @return {String} The result
     */
    SparkMD5.prototype.end = function (raw) {
        var buff = this._buff,
            length = buff.length,
            i,
            tail = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
            ret;

        for (i = 0; i < length; i += 1) {
            tail[i >> 2] |= buff.charCodeAt(i) << ((i % 4) << 3);
        }

        this._finish(tail, length);
        ret = hex(this._hash);

        if (raw) {
            ret = hexToBinaryString(ret);
        }

        this.reset();

        return ret;
    };

    /**
     * Resets the internal state of the computation.
     *
     * @return {SparkMD5} The instance itself
     */
    SparkMD5.prototype.reset = function () {
        this._buff = '';
        this._length = 0;
        this._hash = [1732584193, -271733879, -1732584194, 271733878];

        return this;
    };

    /**
     * Gets the internal state of the computation.
     *
     * @return {Object} The state
     */
    SparkMD5.prototype.getState = function () {
        return {
            buff: this._buff,
            length: this._length,
            hash: this._hash
        };
    };

    /**
     * Gets the internal state of the computation.
     *
     * @param {Object} state The state
     *
     * @return {SparkMD5} The instance itself
     */
    SparkMD5.prototype.setState = function (state) {
        this._buff = state.buff;
        this._length = state.length;
        this._hash = state.hash;

        return this;
    };

    /**
     * Releases memory used by the incremental buffer and other additional
     * resources. If you plan to use the instance again, use reset instead.
     */
    SparkMD5.prototype.destroy = function () {
        delete this._hash;
        delete this._buff;
        delete this._length;
    };

    /**
     * Finish the final calculation based on the tail.
     *
     * @param {Array}  tail   The tail (will be modified)
     * @param {Number} length The length of the remaining buffer
     */
    SparkMD5.prototype._finish = function (tail, length) {
        var i = length,
            tmp,
            lo,
            hi;

        tail[i >> 2] |= 0x80 << ((i % 4) << 3);
        if (i > 55) {
            md5cycle(this._hash, tail);
            for (i = 0; i < 16; i += 1) {
                tail[i] = 0;
            }
        }

        // Do the final computation based on the tail and length
        // Beware that the final length may not fit in 32 bits so we take care of that
        tmp = this._length * 8;
        tmp = tmp.toString(16).match(/(.*?)(.{0,8})$/);
        lo = parseInt(tmp[2], 16);
        hi = parseInt(tmp[1], 16) || 0;

        tail[14] = lo;
        tail[15] = hi;
        md5cycle(this._hash, tail);
    };

    /**
     * Performs the md5 hash on a string.
     * A conversion will be applied if utf8 string is detected.
     *
     * @param {String}  str The string
     * @param {Boolean} raw True to get the raw string, false to get the hex string
     *
     * @return {String} The result
     */
    SparkMD5.hash = function (str, raw) {
        // Converts the string to utf8 bytes if necessary
        // Then compute it using the binary function
        return SparkMD5.hashBinary(toUtf8(str), raw);
    };

    /**
     * Performs the md5 hash on a binary string.
     *
     * @param {String}  content The binary string
     * @param {Boolean} raw     True to get the raw string, false to get the hex string
     *
     * @return {String} The result
     */
    SparkMD5.hashBinary = function (content, raw) {
        var hash = md51(content),
            ret = hex(hash);

        return raw ? hexToBinaryString(ret) : ret;
    };

    // ---------------------------------------------------

    /**
     * SparkMD5 OOP implementation for array buffers.
     *
     * Use this class to perform an incremental md5 ONLY for array buffers.
     */
    SparkMD5.ArrayBuffer = function () {
        // call reset to init the instance
        this.reset();
    };

    /**
     * Appends an array buffer.
     *
     * @param {ArrayBuffer} arr The array to be appended
     *
     * @return {SparkMD5.ArrayBuffer} The instance itself
     */
    SparkMD5.ArrayBuffer.prototype.append = function (arr) {
        var buff = concatenateArrayBuffers(this._buff.buffer, arr, true),
            length = buff.length,
            i;

        this._length += arr.byteLength;

        for (i = 64; i <= length; i += 64) {
            md5cycle(this._hash, md5blk_array(buff.subarray(i - 64, i)));
        }

        this._buff = (i - 64) < length ? new Uint8Array(buff.buffer.slice(i - 64)) : new Uint8Array(0);

        return this;
    };

    /**
     * Finishes the incremental computation, reseting the internal state and
     * returning the result.
     *
     * @param {Boolean} raw True to get the raw string, false to get the hex string
     *
     * @return {String} The result
     */
    SparkMD5.ArrayBuffer.prototype.end = function (raw) {
        var buff = this._buff,
            length = buff.length,
            tail = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
            i,
            ret;

        for (i = 0; i < length; i += 1) {
            tail[i >> 2] |= buff[i] << ((i % 4) << 3);
        }

        this._finish(tail, length);
        ret = hex(this._hash);

        if (raw) {
            ret = hexToBinaryString(ret);
        }

        this.reset();

        return ret;
    };

    /**
     * Resets the internal state of the computation.
     *
     * @return {SparkMD5.ArrayBuffer} The instance itself
     */
    SparkMD5.ArrayBuffer.prototype.reset = function () {
        this._buff = new Uint8Array(0);
        this._length = 0;
        this._hash = [1732584193, -271733879, -1732584194, 271733878];

        return this;
    };

    /**
     * Gets the internal state of the computation.
     *
     * @return {Object} The state
     */
    SparkMD5.ArrayBuffer.prototype.getState = function () {
        var state = SparkMD5.prototype.getState.call(this);

        // Convert buffer to a string
        state.buff = arrayBuffer2Utf8Str(state.buff);

        return state;
    };

    /**
     * Gets the internal state of the computation.
     *
     * @param {Object} state The state
     *
     * @return {SparkMD5.ArrayBuffer} The instance itself
     */
    SparkMD5.ArrayBuffer.prototype.setState = function (state) {
        // Convert string to buffer
        state.buff = utf8Str2ArrayBuffer(state.buff, true);

        return SparkMD5.prototype.setState.call(this, state);
    };

    SparkMD5.ArrayBuffer.prototype.destroy = SparkMD5.prototype.destroy;

    SparkMD5.ArrayBuffer.prototype._finish = SparkMD5.prototype._finish;

    /**
     * Performs the md5 hash on an array buffer.
     *
     * @param {ArrayBuffer} arr The array buffer
     * @param {Boolean}     raw True to get the raw string, false to get the hex one
     *
     * @return {String} The result
     */
    SparkMD5.ArrayBuffer.hash = function (arr, raw) {
        var hash = md51_array(new Uint8Array(arr)),
            ret = hex(hash);

        return raw ? hexToBinaryString(ret) : ret;
    };

    return SparkMD5;
}));

},{}],94:[function(require,module,exports){
(function (setImmediate,clearImmediate){(function (){
var nextTick = require('process/browser.js').nextTick;
var apply = Function.prototype.apply;
var slice = Array.prototype.slice;
var immediateIds = {};
var nextImmediateId = 0;

// DOM APIs, for completeness

exports.setTimeout = function() {
  return new Timeout(apply.call(setTimeout, window, arguments), clearTimeout);
};
exports.setInterval = function() {
  return new Timeout(apply.call(setInterval, window, arguments), clearInterval);
};
exports.clearTimeout =
exports.clearInterval = function(timeout) { timeout.close(); };

function Timeout(id, clearFn) {
  this._id = id;
  this._clearFn = clearFn;
}
Timeout.prototype.unref = Timeout.prototype.ref = function() {};
Timeout.prototype.close = function() {
  this._clearFn.call(window, this._id);
};

// Does not start the time, just sets up the members needed.
exports.enroll = function(item, msecs) {
  clearTimeout(item._idleTimeoutId);
  item._idleTimeout = msecs;
};

exports.unenroll = function(item) {
  clearTimeout(item._idleTimeoutId);
  item._idleTimeout = -1;
};

exports._unrefActive = exports.active = function(item) {
  clearTimeout(item._idleTimeoutId);

  var msecs = item._idleTimeout;
  if (msecs >= 0) {
    item._idleTimeoutId = setTimeout(function onTimeout() {
      if (item._onTimeout)
        item._onTimeout();
    }, msecs);
  }
};

// That's not how node.js implements it but the exposed api is the same.
exports.setImmediate = typeof setImmediate === "function" ? setImmediate : function(fn) {
  var id = nextImmediateId++;
  var args = arguments.length < 2 ? false : slice.call(arguments, 1);

  immediateIds[id] = true;

  nextTick(function onNextTick() {
    if (immediateIds[id]) {
      // fn.call() is faster so we optimize for the common use-case
      // @see http://jsperf.com/call-apply-segu
      if (args) {
        fn.apply(null, args);
      } else {
        fn.call(null);
      }
      // Prevent ids from leaking
      exports.clearImmediate(id);
    }
  });

  return id;
};

exports.clearImmediate = typeof clearImmediate === "function" ? clearImmediate : function(id) {
  delete immediateIds[id];
};
}).call(this)}).call(this,require("timers").setImmediate,require("timers").clearImmediate)
},{"process/browser.js":78,"timers":94}],95:[function(require,module,exports){

/**
 * Module Dependencies
 */

var expr;
try {
  expr = require('props');
} catch(e) {
  expr = require('component-props');
}

/**
 * Expose `toFunction()`.
 */

module.exports = toFunction;

/**
 * Convert `obj` to a `Function`.
 *
 * @param {Mixed} obj
 * @return {Function}
 * @api private
 */

function toFunction(obj) {
  switch ({}.toString.call(obj)) {
    case '[object Object]':
      return objectToFunction(obj);
    case '[object Function]':
      return obj;
    case '[object String]':
      return stringToFunction(obj);
    case '[object RegExp]':
      return regexpToFunction(obj);
    default:
      return defaultToFunction(obj);
  }
}

/**
 * Default to strict equality.
 *
 * @param {Mixed} val
 * @return {Function}
 * @api private
 */

function defaultToFunction(val) {
  return function(obj){
    return val === obj;
  };
}

/**
 * Convert `re` to a function.
 *
 * @param {RegExp} re
 * @return {Function}
 * @api private
 */

function regexpToFunction(re) {
  return function(obj){
    return re.test(obj);
  };
}

/**
 * Convert property `str` to a function.
 *
 * @param {String} str
 * @return {Function}
 * @api private
 */

function stringToFunction(str) {
  // immediate such as "> 20"
  if (/^ *\W+/.test(str)) return new Function('_', 'return _ ' + str);

  // properties such as "name.first" or "age > 18" or "age > 18 && age < 36"
  return new Function('_', 'return ' + get(str));
}

/**
 * Convert `object` to a function.
 *
 * @param {Object} object
 * @return {Function}
 * @api private
 */

function objectToFunction(obj) {
  var match = {};
  for (var key in obj) {
    match[key] = typeof obj[key] === 'string'
      ? defaultToFunction(obj[key])
      : toFunction(obj[key]);
  }
  return function(val){
    if (typeof val !== 'object') return false;
    for (var key in match) {
      if (!(key in val)) return false;
      if (!match[key](val[key])) return false;
    }
    return true;
  };
}

/**
 * Built the getter function. Supports getter style functions
 *
 * @param {String} str
 * @return {String}
 * @api private
 */

function get(str) {
  var props = expr(str);
  if (!props.length) return '_.' + str;

  var val, i, prop;
  for (i = 0; i < props.length; i++) {
    prop = props[i];
    val = '_.' + prop;
    val = "('function' == typeof " + val + " ? " + val + "() : " + val + ")";

    // mimic negative lookbehind to avoid problems with nested properties
    str = stripNested(prop, str, val);
  }

  return str;
}

/**
 * Mimic negative lookbehind to avoid problems with nested properties.
 *
 * See: http://blog.stevenlevithan.com/archives/mimic-lookbehind-javascript
 *
 * @param {String} prop
 * @param {String} str
 * @param {String} val
 * @return {String}
 * @api private
 */

function stripNested (prop, str, val) {
  return str.replace(new RegExp('(\\.)?' + prop, 'g'), function($0, $1) {
    return $1 ? $0 : val;
  });
}

},{"component-props":54,"props":54}],96:[function(require,module,exports){

/**
 * Expose `toNoCase`.
 */

module.exports = toNoCase;


/**
 * Test whether a string is camel-case.
 */

var hasSpace = /\s/;
var hasSeparator = /[\W_]/;


/**
 * Remove any starting case from a `string`, like camel or snake, but keep
 * spaces and punctuation that may be important otherwise.
 *
 * @param {String} string
 * @return {String}
 */

function toNoCase (string) {
  if (hasSpace.test(string)) return string.toLowerCase();
  if (hasSeparator.test(string)) return (unseparate(string) || string).toLowerCase();
  return uncamelize(string).toLowerCase();
}


/**
 * Separator splitter.
 */

var separatorSplitter = /[\W_]+(.|$)/g;


/**
 * Un-separate a `string`.
 *
 * @param {String} string
 * @return {String}
 */

function unseparate (string) {
  return string.replace(separatorSplitter, function (m, next) {
    return next ? ' ' + next : '';
  });
}


/**
 * Camelcase splitter.
 */

var camelSplitter = /(.)([A-Z]+)/g;


/**
 * Un-camelcase a `string`.
 *
 * @param {String} string
 * @return {String}
 */

function uncamelize (string) {
  return string.replace(camelSplitter, function (m, previous, uppers) {
    return previous + ' ' + uppers.toLowerCase().split('').join(' ');
  });
}
},{}],97:[function(require,module,exports){

exports = module.exports = trim;

function trim(str){
  return str.replace(/^\s*|\s*$/g, '');
}

exports.left = function(str){
  return str.replace(/^\s*/, '');
};

exports.right = function(str){
  return str.replace(/\s*$/, '');
};

},{}],98:[function(require,module,exports){

/**
 * toString ref.
 */

var toString = Object.prototype.toString;

/**
 * Return the type of `val`.
 *
 * @param {Mixed} val
 * @return {String}
 * @api public
 */

module.exports = function(val){
  switch (toString.call(val)) {
    case '[object Function]': return 'function';
    case '[object Date]': return 'date';
    case '[object RegExp]': return 'regexp';
    case '[object Arguments]': return 'arguments';
    case '[object Array]': return 'array';
  }

  if (val === null) return 'null';
  if (val === undefined) return 'undefined';
  if (val === Object(val)) return 'object';

  return typeof val;
};

},{}],99:[function(require,module,exports){
module.exports = encode;

function encode(string) {
    string = string.replace(/\r\n/g, "\n");
    var utftext = "";

    for (var n = 0; n < string.length; n++) {

        var c = string.charCodeAt(n);

        if (c < 128) {
            utftext += String.fromCharCode(c);
        }
        else if ((c > 127) && (c < 2048)) {
            utftext += String.fromCharCode((c >> 6) | 192);
            utftext += String.fromCharCode((c & 63) | 128);
        }
        else {
            utftext += String.fromCharCode((c >> 12) | 224);
            utftext += String.fromCharCode(((c >> 6) & 63) | 128);
            utftext += String.fromCharCode((c & 63) | 128);
        }

    }

    return utftext;
}
},{}],100:[function(require,module,exports){
(function (global){(function (){

var rng;

var crypto = global.crypto || global.msCrypto; // for IE 11
if (crypto && crypto.getRandomValues) {
  // WHATWG crypto-based RNG - http://wiki.whatwg.org/wiki/Crypto
  // Moderately fast, high quality
  var _rnds8 = new Uint8Array(16);
  rng = function whatwgRNG() {
    crypto.getRandomValues(_rnds8);
    return _rnds8;
  };
}

if (!rng) {
  // Math.random()-based (RNG)
  //
  // If all else fails, use Math.random().  It's fast, but is of unspecified
  // quality.
  var  _rnds = new Array(16);
  rng = function() {
    for (var i = 0, r; i < 16; i++) {
      if ((i & 0x03) === 0) r = Math.random() * 0x100000000;
      _rnds[i] = r >>> ((i & 0x03) << 3) & 0xff;
    }

    return _rnds;
  };
}

module.exports = rng;


}).call(this)}).call(this,typeof global !== "undefined" ? global : typeof self !== "undefined" ? self : typeof window !== "undefined" ? window : {})
},{}],101:[function(require,module,exports){
//     uuid.js
//
//     Copyright (c) 2010-2012 Robert Kieffer
//     MIT License - http://opensource.org/licenses/mit-license.php

// Unique ID creation requires a high quality random # generator.  We feature
// detect to determine the best RNG source, normalizing to a function that
// returns 128-bits of randomness, since that's what's usually required
var _rng = require('./rng');

// Maps for number <-> hex string conversion
var _byteToHex = [];
var _hexToByte = {};
for (var i = 0; i < 256; i++) {
  _byteToHex[i] = (i + 0x100).toString(16).substr(1);
  _hexToByte[_byteToHex[i]] = i;
}

// **`parse()` - Parse a UUID into it's component bytes**
function parse(s, buf, offset) {
  var i = (buf && offset) || 0, ii = 0;

  buf = buf || [];
  s.toLowerCase().replace(/[0-9a-f]{2}/g, function(oct) {
    if (ii < 16) { // Don't overflow!
      buf[i + ii++] = _hexToByte[oct];
    }
  });

  // Zero out remaining bytes if string was short
  while (ii < 16) {
    buf[i + ii++] = 0;
  }

  return buf;
}

// **`unparse()` - Convert UUID byte array (ala parse()) into a string**
function unparse(buf, offset) {
  var i = offset || 0, bth = _byteToHex;
  return  bth[buf[i++]] + bth[buf[i++]] +
          bth[buf[i++]] + bth[buf[i++]] + '-' +
          bth[buf[i++]] + bth[buf[i++]] + '-' +
          bth[buf[i++]] + bth[buf[i++]] + '-' +
          bth[buf[i++]] + bth[buf[i++]] + '-' +
          bth[buf[i++]] + bth[buf[i++]] +
          bth[buf[i++]] + bth[buf[i++]] +
          bth[buf[i++]] + bth[buf[i++]];
}

// **`v1()` - Generate time-based UUID**
//
// Inspired by https://github.com/LiosK/UUID.js
// and http://docs.python.org/library/uuid.html

// random #'s we need to init node and clockseq
var _seedBytes = _rng();

// Per 4.5, create and 48-bit node id, (47 random bits + multicast bit = 1)
var _nodeId = [
  _seedBytes[0] | 0x01,
  _seedBytes[1], _seedBytes[2], _seedBytes[3], _seedBytes[4], _seedBytes[5]
];

// Per 4.2.2, randomize (14 bit) clockseq
var _clockseq = (_seedBytes[6] << 8 | _seedBytes[7]) & 0x3fff;

// Previous uuid creation time
var _lastMSecs = 0, _lastNSecs = 0;

// See https://github.com/broofa/node-uuid for API details
function v1(options, buf, offset) {
  var i = buf && offset || 0;
  var b = buf || [];

  options = options || {};

  var clockseq = options.clockseq !== undefined ? options.clockseq : _clockseq;

  // UUID timestamps are 100 nano-second units since the Gregorian epoch,
  // (1582-10-15 00:00).  JSNumbers aren't precise enough for this, so
  // time is handled internally as 'msecs' (integer milliseconds) and 'nsecs'
  // (100-nanoseconds offset from msecs) since unix epoch, 1970-01-01 00:00.
  var msecs = options.msecs !== undefined ? options.msecs : new Date().getTime();

  // Per 4.2.1.2, use count of uuid's generated during the current clock
  // cycle to simulate higher resolution clock
  var nsecs = options.nsecs !== undefined ? options.nsecs : _lastNSecs + 1;

  // Time since last uuid creation (in msecs)
  var dt = (msecs - _lastMSecs) + (nsecs - _lastNSecs)/10000;

  // Per 4.2.1.2, Bump clockseq on clock regression
  if (dt < 0 && options.clockseq === undefined) {
    clockseq = clockseq + 1 & 0x3fff;
  }

  // Reset nsecs if clock regresses (new clockseq) or we've moved onto a new
  // time interval
  if ((dt < 0 || msecs > _lastMSecs) && options.nsecs === undefined) {
    nsecs = 0;
  }

  // Per 4.2.1.2 Throw error if too many uuids are requested
  if (nsecs >= 10000) {
    throw new Error('uuid.v1(): Can\'t create more than 10M uuids/sec');
  }

  _lastMSecs = msecs;
  _lastNSecs = nsecs;
  _clockseq = clockseq;

  // Per 4.1.4 - Convert from unix epoch to Gregorian epoch
  msecs += 12219292800000;

  // `time_low`
  var tl = ((msecs & 0xfffffff) * 10000 + nsecs) % 0x100000000;
  b[i++] = tl >>> 24 & 0xff;
  b[i++] = tl >>> 16 & 0xff;
  b[i++] = tl >>> 8 & 0xff;
  b[i++] = tl & 0xff;

  // `time_mid`
  var tmh = (msecs / 0x100000000 * 10000) & 0xfffffff;
  b[i++] = tmh >>> 8 & 0xff;
  b[i++] = tmh & 0xff;

  // `time_high_and_version`
  b[i++] = tmh >>> 24 & 0xf | 0x10; // include version
  b[i++] = tmh >>> 16 & 0xff;

  // `clock_seq_hi_and_reserved` (Per 4.2.2 - include variant)
  b[i++] = clockseq >>> 8 | 0x80;

  // `clock_seq_low`
  b[i++] = clockseq & 0xff;

  // `node`
  var node = options.node || _nodeId;
  for (var n = 0; n < 6; n++) {
    b[i + n] = node[n];
  }

  return buf ? buf : unparse(b);
}

// **`v4()` - Generate random UUID**

// See https://github.com/broofa/node-uuid for API details
function v4(options, buf, offset) {
  // Deprecated - 'format' argument, as supported in v1.2
  var i = buf && offset || 0;

  if (typeof(options) == 'string') {
    buf = options == 'binary' ? new Array(16) : null;
    options = null;
  }
  options = options || {};

  var rnds = options.random || (options.rng || _rng)();

  // Per 4.4, set bits for version and `clock_seq_hi_and_reserved`
  rnds[6] = (rnds[6] & 0x0f) | 0x40;
  rnds[8] = (rnds[8] & 0x3f) | 0x80;

  // Copy bytes to buffer, if provided
  if (buf) {
    for (var ii = 0; ii < 16; ii++) {
      buf[i + ii] = rnds[ii];
    }
  }

  return buf || unparse(rnds);
}

// Export public API
var uuid = v4;
uuid.v1 = v1;
uuid.v4 = v4;
uuid.parse = parse;
uuid.unparse = unparse;

module.exports = uuid;

},{"./rng":100}],102:[function(require,module,exports){
module.exports={
  "name": "unomi-analytics",
  "version": "1.0.9",
  "description": "The Apache Unomi analytics.js integration.",
  "main": "dist/unomi-tracker.js",
  "keywords": [
    "unomi",
    "analytics.js",
    "apache"
  ],
  "author": "Apache Software Foundation",
  "license": "Apache-2.0",
  "scripts": {
    "build": "yarn browserify && yarn snippet:browserify && yarn replace && yarn minify && yarn snippet:minify",
    "browserify": "browserify src/index.js -p [ browserify-header --file src/license.js ] -s unomiTracker -o dist/unomi-tracker.js",
    "snippet:browserify": "browserify src/snippet.js -o dist/snippet.js",
    "replace": "replace-in-file 'analytics.require = require' '//analytics.require = require' dist/unomi-tracker.js",
    "minify": "cd dist && uglifyjs -c -m --comments '/@license/' -o unomi-tracker.min.js --source-map url=unomi-tracker.min.js.map -- unomi-tracker.js",
    "snippet:minify": "cd dist && uglifyjs -c -m -o snippet.min.js --source-map url=snipper.min.js.map -- snippet.js",
    "clean": "rimraf *.log dist/*",
    "clean:all": "yarn clean && rimraf node_modules"
  },
  "dependencies": {
    "@segment/analytics.js-core": "3.10.1",
    "@segment/analytics.js-integration": "3.2.1",
    "extend": "^3.0.2"
  },
  "devDependencies": {
    "browserify": "^16.5.1",
    "browserify-header": "^1.0.1",
    "replace-in-file": "^3.4.2",
    "rimraf": "^2.6.2",
    "uglify-js": "^3.9.2",
    "yarn": "^1.22.4"
  }
}

},{}],103:[function(require,module,exports){
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

var integration = require('@segment/analytics.js-integration');
var extend  = require('extend');

var Unomi = (module.exports = integration('Apache Unomi')
    .global('cxs')
    .assumesPageview()
    .readyOnLoad()
    .option('scope', 'systemscope')
    .option('url', 'http://localhost:8181')
    .option('timeoutInMilliseconds', 3000)
    .option('sessionCookieName', 'unomiSessionId')
    .option('sessionId'));

/**
 * Initialize.
 *
 * @api public
 */
Unomi.prototype.initialize = function() {
    var self = this;

    console.info('[UNOMI] Initializing...', arguments);

    this.analytics.on('invoke', function(msg) {
        var action = msg.action();
        var listener = 'on' + msg.action();
        self.debug('%s %o', action, msg);
        if (self[listener]) self[listener](msg);
    });

    this.analytics.personalize = function(personalization, callback) {
        this.emit('invoke', {action:function() {return "personalize"}, personalization:personalization, callback:callback});
    };

    // Standard to check if cookies are enabled in this browser
    if (!navigator.cookieEnabled) {
        this.executeFallback();
        return;
    }

    // digitalData come from a standard so we can keep the logic around it which can allow complex website to load more complex data
    window.digitalData = window.digitalData || {
        scope: self.options.scope
    };

    window.digitalData.page = window.digitalData.page || {
        path: location.pathname + location.hash,
        pageInfo: {
            pageName: document.title,
            pageID : location.pathname + location.hash,
            pagePath : location.pathname + location.hash,
            destinationURL: location.href
        }
    }

    var unomiPage = window.digitalData.page;
    if (!unomiPage) {
        unomiPage = window.digitalData.page = { pageInfo:{} }
    }
    if (self.options.initialPageProperties) {
        var props = self.options.initialPageProperties;
        this.fillPageData(unomiPage, props);
    }
    window.digitalData.events = window.digitalData.events || [];
    window.digitalData.events.push(this.buildEvent('view', this.buildPage(unomiPage), this.buildSource(this.options.scope, 'site')))

    if (!self.options.sessionId) {
        var cookie = require('component-cookie');

        self.sessionId = cookie(self.options.sessionCookieName);
        // so we should not need to implement our own
        if (!self.sessionId || self.sessionId === '') {
            self.sessionId = self.generateGuid();
            cookie(self.options.sessionCookieName, self.sessionId);
        }
    } else {
        this.sessionId = self.options.sessionId;
    }

    setTimeout(self.loadContext.bind(self), 0);
};

/**
 * Loaded.
 *
 * @api private
 * @return {boolean}
 */
Unomi.prototype.loaded = function() {
    return !!window.cxs;
};

/**
 * Page.
 *
 * @api public
 * @param {Page} page
 */
Unomi.prototype.page = function(page) {
    var unomiPage = { };
    this.fillPageData(unomiPage, page.json().properties);

    this.collectEvent(this.buildEvent('view', this.buildPage(unomiPage), this.buildSource(this.options.scope, 'site')));
};

Unomi.prototype.fillPageData = function(unomiPage, props) {
    unomiPage.attributes = [];
    unomiPage.consentTypes = [];
    unomiPage.interests = props.interests || {};
    unomiPage.pageInfo = extend({}, unomiPage.pageInfo, props.pageInfo);
    unomiPage.pageInfo.pageName = unomiPage.pageInfo.pageName || props.title;
    unomiPage.pageInfo.pageID = unomiPage.pageInfo.pageID || props.path;
    unomiPage.pageInfo.pagePath = unomiPage.pageInfo.pagePath || props.path;
    unomiPage.pageInfo.destinationURL = unomiPage.pageInfo.destinationURL || props.url;
    unomiPage.pageInfo.referringURL = unomiPage.pageInfo.referringURL || props.referrer;
    this.processReferrer();
};

Unomi.prototype.processReferrer = function() {
    var referrerURL = document.referrer;
    if (referrerURL) {
        // parse referrer URL
        var referrer = document.createElement('a');
        referrer.href = referrerURL;

        // only process referrer if it's not coming from the same site as the current page
        var local = document.createElement('a');
        local.href = document.URL;
        if (referrer.host !== local.host) {
            // get search element if it exists and extract search query if available
            var search = referrer.search;
            var query = undefined;
            if (search && search != '') {
                // parse parameters
                var queryParams = [], param;
                var queryParamPairs = search.slice(1).split('&');
                for (var i = 0; i < queryParamPairs.length; i++) {
                    param = queryParamPairs[i].split('=');
                    queryParams.push(param[0]);
                    queryParams[param[0]] = param[1];
                }

                // try to extract query: q is Google-like (most search engines), p is Yahoo
                query = queryParams.q || queryParams.p;
                query = decodeURIComponent(query).replace(/\+/g, ' ');
            }

            // add data to digitalData
            if (window.digitalData && window.digitalData.page && window.digitalData.page.pageInfo) {
                window.digitalData.page.pageInfo.referrerHost = referrer.host;
                window.digitalData.page.pageInfo.referrerQuery = query;
            }

            // register referrer event
            this.registerEvent(this.buildEvent('viewFromReferrer', this.buildTargetPage()));
        }
    }
};


/**
 * Identify.
 *
 * @api public
 * @param {Identify} identify
 */
Unomi.prototype.identify = function(identify) {
    this.collectEvent(this.buildEvent("identify",
        this.buildTarget(identify.userId(), "analyticsUser", identify.traits()),
        this.buildSource(this.options.scope, 'site', identify.context())));
};

/**
 * ontrack.
 *
 * @api private
 * @param {Track} track
 */
Unomi.prototype.track = function(track) {
    // we use the track event name to know that we are submitted a form because Analytics.js trackForm method doesn't give
    // us another way of knowing that we are processing a form.
    if (track.event() && track.event().indexOf("form") === 0) {
        var form = document.forms[track.properties().formName];
        var formEvent = this.buildFormEvent(form.name);
        formEvent.properties = this.extractFormData(form);
        this.collectEvent(formEvent);
    } else {
        this.collectEvent(this.buildEvent(track.event(),
            this.buildTargetPage(),
            this.buildSource(this.options.scope, 'site', track.context()),
            track.properties()
        ));
    }
};

/**
 * This function is used to load the current context in the page
 *
 * @param {boolean} [skipEvents=false] Should we send the events
 * @param {boolean} [invalidate=false] Should we invalidate the current context
 */
Unomi.prototype.loadContext = function (skipEvents, invalidate) {
    this.contextLoaded = true;
    var jsonData = {
        requiredProfileProperties: ['j:nodename'],
        source: this.buildPage(window.digitalData.page)
    };
    if (!skipEvents) {
        jsonData.events = window.digitalData.events
    }
    if (window.digitalData.personalizationCallback) {
        jsonData.personalizations = window.digitalData.personalizationCallback.map(function (x) {
            return x.personalization
        })
    }

    jsonData.sessionId = this.sessionId;

    var contextUrl = this.options.url + '/cxs/context.json';
    if (invalidate) {
        contextUrl += '?invalidateSession=true&invalidateProfile=true';
    }

    var self = this;

    var onSuccess = function (xhr) {

        window.cxs = JSON.parse(xhr.responseText);

        self.ready();

        if (window.digitalData.loadCallbacks) {
            console.info('[UNOMI] Found context server load callbacks, calling now...');
            for (var i = 0; i < window.digitalData.loadCallbacks.length; i++) {
                window.digitalData.loadCallbacks[i](digitalData);
            }
        }
        if (window.digitalData.personalizationCallback) {
            console.info('[UNOMI] Found context server personalization, calling now...');
            for (var i = 0; i < window.digitalData.personalizationCallback.length; i++) {
                window.digitalData.personalizationCallback[i].callback(cxs.personalizations[window.digitalData.personalizationCallback[i].personalization.id]);
            }
        }
    };

    this.ajax({
        url: contextUrl,
        type: 'POST',
        async: true,
        contentType: 'text/plain;charset=UTF-8', // Use text/plain to avoid CORS preflight
        jsonData: jsonData,
        dataType: 'application/json',
        invalidate: invalidate,
        success: onSuccess,
        error: this.executeFallback
    });

    console.info('[UNOMI] Context loading...');
};

Unomi.prototype.onpersonalize = function (msg) {
    if (this.contextLoaded) {
        console.error('[UNOMI] Already loaded, too late...');
        return;
    }
    window.digitalData = window.digitalData || {
        scope: this.options.scope
    };
    window.digitalData.personalizationCallback = window.digitalData.personalizationCallback || [];
    window.digitalData.personalizationCallback.push({personalization: msg.personalization, callback: msg.callback});
};

/**
 * This function return the basic structure for an event, it must be adapted to your need
 *
 * @param {string} eventType The name of your event
 * @param {object} [target] The target object for your event can be build with this.buildTarget(targetId, targetType, targetProperties)
 * @param {object} [source] The source object for your event can be build with this.buildSource(sourceId, sourceType, sourceProperties)
 * @param {object} [properties] a map of properties for the event
 * @returns {{eventType: *, scope}}
 */
Unomi.prototype.buildEvent = function (eventType, target, source, properties) {
    var event = {
        eventType: eventType,
        scope: window.digitalData.scope
    };

    if (target) {
        event.target = target;
    }

    if (source) {
        event.source = source;
    }

    if (properties) {
        event.properties = properties;
    }

    return event;
};

/**
 * This function return an event of type form
 *
 * @param {string} formName The HTML name of id of the form to use in the target of the event
 * @returns {*|{eventType: *, scope, source: {scope, itemId: string, itemType: string, properties: {}}, target: {scope, itemId: string, itemType: string, properties: {}}}}
 */
Unomi.prototype.buildFormEvent = function (formName) {
    return this.buildEvent('form', this.buildTarget(formName, 'form'), this.buildSourcePage());
};

/**
 * This function return the source object for a source of type page
 *
 * @returns {*|{scope, itemId: *, itemType: *}}
 */
Unomi.prototype.buildTargetPage = function () {
    return this.buildTarget(window.digitalData.page.pageInfo.pageID, 'page', window.digitalData.page);
};

/**
 * This function return the source object for a source of type page
 *
 * @returns {*|{scope, itemId: *, itemType: *}}
 */
Unomi.prototype.buildSourcePage = function () {
    return this.buildSource(window.digitalData.page.pageInfo.pageID, 'page', window.digitalData.page);
};


/**
 * This function return the source object for a source of type page
 *
 * @returns {*|{scope, itemId: *, itemType: *}}
 */
Unomi.prototype.buildPage = function (page) {
    return this.buildSource(page.pageInfo.pageID, 'page', page);
};

/**
 * This function return the basic structure for the target of your event
 *
 * @param {string} targetId The ID of the target
 * @param {string} targetType The type of the target
 * @param {object} [targetProperties] The optional properties of the target
 * @returns {{scope, itemId: *, itemType: *}}
 */
Unomi.prototype.buildTarget = function (targetId, targetType, targetProperties) {
    return this.buildObject(targetId, targetType, targetProperties);
};

/**
 * This function return the basic structure for the source of your event
 *
 * @param {string} sourceId The ID of the source
 * @param {string} sourceType The type of the source
 * @param {object} [sourceProperties] The optional properties of the source
 * @returns {{scope, itemId: *, itemType: *}}
 */
Unomi.prototype.buildSource = function (sourceId, sourceType, sourceProperties) {
    return this.buildObject(sourceId, sourceType, sourceProperties);
};


/**
 * This function will send an event to Apache Unomi
 * @param {object} event The event object to send, you can build it using this.buildEvent(eventType, target, source)
 * @param {function} successCallback will be executed in case of success
 * @param {function} errorCallback will be executed in case of error
 */
Unomi.prototype.collectEvent = function (event, successCallback, errorCallback) {
    this.collectEvents({events: [event]}, successCallback, errorCallback);
};

/**
 * This function will send the events to Apache Unomi
 *
 * @param {object} events Javascript object { events: [event1, event2] }
 * @param {function} successCallback will be executed in case of success
 * @param {function} errorCallback will be executed in case of error
 */
Unomi.prototype.collectEvents = function (events, successCallback, errorCallback) {
    events.sessionId = this.sessionId;

    var data = JSON.stringify(events);
    this.ajax({
        url: this.options.url + '/cxs/eventcollector',
        type: 'POST',
        async: true,
        contentType: 'text/plain;charset=UTF-8', // Use text/plain to avoid CORS preflight
        data: data,
        dataType: 'application/json',
        success: successCallback,
        error: errorCallback
    });
};

/*******************************/
/* Private Function under this */
/*******************************/

Unomi.prototype.registerEvent = function (event) {
    if (window.digitalData) {
        if (window.cxs) {
            console.error('[UNOMI] already loaded, too late...');
        } else {
            window.digitalData.events = window.digitalData.events || [];
            window.digitalData.events.push(event);
        }
    } else {
        window.digitalData = {};
        window.digitalData.events = window.digitalData.events || [];
        window.digitalData.events.push(event);
    }
};

Unomi.prototype.registerCallback = function (onLoadCallback) {
    if (window.digitalData) {
        if (window.cxs) {
            console.info('[UNOMI] digitalData object loaded, calling on load callback immediately and registering update callback...');
            if (onLoadCallback) {
                onLoadCallback(window.digitalData);
            }
        } else {
            console.info('[UNOMI] digitalData object present but not loaded, registering load callback...');
            if (onLoadCallback) {
                window.digitalData.loadCallbacks = window.digitalData.loadCallbacks || [];
                window.digitalData.loadCallbacks.push(onLoadCallback);
            }
        }
    } else {
        console.info('[UNOMI] No digital data object found, creating and registering update callback...');
        window.digitalData = {};
        if (onLoadCallback) {
            window.digitalData.loadCallbacks = [];
            window.digitalData.loadCallbacks.push(onLoadCallback);
        }
    }
};

/**
 * This is an utility function to generate a new UUID
 *
 * @returns {string}
 */
Unomi.prototype.generateGuid = function () {
    function s4() {
        return Math.floor((1 + Math.random()) * 0x10000)
            .toString(16)
            .substring(1);
    }

    return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
        s4() + '-' + s4() + s4() + s4();
};

Unomi.prototype.buildObject = function (itemId, itemType, properties) {
    var object = {
        scope: window.digitalData.scope,
        itemId: itemId,
        itemType: itemType
    };

    if (properties) {
        object.properties = properties;
    }

    return object;
};

/**
 * This is an utility function to execute AJAX call
 *
 * @param {object} ajaxOptions
 */
Unomi.prototype.ajax = function (ajaxOptions) {
    var xhr = new XMLHttpRequest();
    if ('withCredentials' in xhr) {
        xhr.open(ajaxOptions.type, ajaxOptions.url, ajaxOptions.async);
        xhr.withCredentials = true;
    } else if (typeof XDomainRequest != 'undefined') {
        xhr = new XDomainRequest();
        xhr.open(ajaxOptions.type, ajaxOptions.url);
    }

    if (ajaxOptions.contentType) {
        xhr.setRequestHeader('Content-Type', ajaxOptions.contentType);
    }
    if (ajaxOptions.dataType) {
        xhr.setRequestHeader('Accept', ajaxOptions.dataType);
    }

    if (ajaxOptions.responseType) {
        xhr.responseType = ajaxOptions.responseType;
    }

    var requestExecuted = false;
    if (this.options.timeoutInMilliseconds !== -1) {
        setTimeout(function () {
            if (!requestExecuted) {
                console.error('[UNOMI] XML request timeout, url: ' + ajaxOptions.url);
                requestExecuted = true;
                if (ajaxOptions.error) {
                    ajaxOptions.error(xhr);
                }
            }
        }, this.options.timeoutInMilliseconds);
    }

    xhr.onreadystatechange = function () {
        if (!requestExecuted) {
            if (xhr.readyState === 4) {
                if (xhr.status === 200 || xhr.status === 204 || xhr.status === 304) {
                    if (xhr.responseText != null) {
                        requestExecuted = true;
                        if (ajaxOptions.success) {
                            ajaxOptions.success(xhr);
                        }
                    }
                } else {
                    requestExecuted = true;
                    if (ajaxOptions.error) {
                        ajaxOptions.error(xhr);
                    }
                    console.error('[UNOMI] XML request error: ' + xhr.statusText + ' (' + xhr.status + ')');
                }
            }
        }
    };

    if (ajaxOptions.jsonData) {
        xhr.send(JSON.stringify(ajaxOptions.jsonData));
    } else if (ajaxOptions.data) {
        xhr.send(ajaxOptions.data);
    } else {
        xhr.send();
    }
};

Unomi.prototype.executeFallback = function () {
    console.warn('[UNOMI] execute fallback');
    window.cxs = {};
    for (var index in window.digitalData.loadCallbacks) {
        window.digitalData.loadCallbacks[index]();
    }
    if (window.digitalData.personalizationCallback) {
        for (var i = 0; i < window.digitalData.personalizationCallback.length; i++) {
            window.digitalData.personalizationCallback[i].callback([window.digitalData.personalizationCallback[i].personalization.strategyOptions.fallback]);
        }
    }
};

Unomi.prototype.extractFormData = function (form) {
    var params = {};
    for (var i = 0; i < form.elements.length; i++) {
        var e = form.elements[i];
        if (typeof(e.name) != 'undefined') {
            switch (e.nodeName) {
                case 'TEXTAREA':
                case 'INPUT':
                    switch (e.type) {
                        case 'checkbox':
                            var checkboxes = document.querySelectorAll('input[name="' + e.name + '"]');
                            if (checkboxes.length > 1) {
                                if (!params[e.name]) {
                                    params[e.name] = [];
                                }
                                if (e.checked) {
                                    params[e.name].push(e.value);
                                }

                            }
                            break;
                        case 'radio':
                            if (e.checked) {
                                params[e.name] = e.value;
                            }
                            break;
                        default:
                            if (!e.value || e.value === '') {
                                // ignore element if no value is provided
                                break;
                            }
                            params[e.name] = e.value;
                    }
                    break;
                case 'SELECT':
                    if (e.options && e.options[e.selectedIndex]) {
                        if (e.multiple) {
                            params[e.name] = [];
                            for (var j = 0; j < e.options.length; j++) {
                                if (e.options[j].selected) {
                                    params[e.name].push(e.options[j].value);
                                }
                            }
                        } else {
                            params[e.name] = e.options[e.selectedIndex].value;
                        }
                    }
                    break;
                default:
                    console.warn("[UNOMI] " + e.nodeName + " form element type not implemented and will not be tracked.");
            }
        }
    }
    return params;
};

},{"@segment/analytics.js-integration":29,"component-cookie":46,"extend":62}],104:[function(require,module,exports){
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Analytics.js
 *
 * (C) 2017 Segment Inc.
 */

var analytics = require('@segment/analytics.js-core');
var Integrations = require('./integrations');

/**
 * Expose the `analytics` singleton.
 */

module.exports = exports = analytics;

/**
 * Expose require.
 */

analytics.require = require

/**
 * Expose `VERSION`.
 */

exports.VERSION = require('../package.json').version;

/**
 * Add integrations.
 */

for (var integration in Integrations) {
    analytics.use(Integrations[integration]);
}
},{"../package.json":102,"./integrations":105,"@segment/analytics.js-core":19}],105:[function(require,module,exports){
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* eslint quote-props: 0 */
'use strict';

module.exports = {
    'apache-unomi': require('./analytics.js-integration-apache-unomi')
};

},{"./analytics.js-integration-apache-unomi":103}]},{},[104])(104)
});
