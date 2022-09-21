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
import commonjs from '@rollup/plugin-commonjs';
import noderesolve from '@rollup/plugin-node-resolve';
import nodePolyfills from 'rollup-plugin-polyfill-node';
import babel from '@rollup/plugin-babel';
import { terser } from 'rollup-plugin-terser';

// iife (immediately invoked function expression) Used to avoid collision in the browser page with others JS
export default {
    input: 'src/javascript/main.js',
    output: {
        format: 'iife',
        file: 'dist/unomi-web-tracker.min.js',
        name: 'unomiWebTracker'
    },
    plugins: [
        commonjs(), // handle: require
        noderesolve(), // resolve modules from node_modules
        nodePolyfills(), // the apache-unomi-tracker is using Buffer internally (crawler bot detection), it needs to be polyfill for browser usage
        babel({ babelHelpers: 'bundled' }), // transpilation
        terser() // minification
    ]
};