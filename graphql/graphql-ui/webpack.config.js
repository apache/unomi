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
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const path = require('path');

module.exports = (env, argv) => {
    const isProd = argv.mode === 'production';

    return {
        context: path.join(__dirname, '/src/main/resources/assets'),
        mode: isProd ? 'production' : 'development',
        devtool: isProd ? false : 'source-map',
        entry: {
            'js/index': './js/index.jsx',
            'css/index': './styles/index.less',
        },
        output: {
            path: path.join(__dirname, '/target/assets'),
        },
        resolve: {
            extensions: ['.js', '.jsx', '.less', '.css'],
        },
        module: {
            rules: [
                {
                    test: /\.(js|jsx)$/,
                    exclude: /node_modules/,
                    use: ['babel-loader']
                },
                {
                    test: /\.(png|svg|jpg|gif)$/,
                    use: [
                        'file-loader',
                    ],
                },
                {
                    test: /\.less$/,
                    use: [
                        {loader: MiniCssExtractPlugin.loader, options: {publicPath: '../'}},
                        {loader: 'css-loader', options: {sourceMap: !isProd, importLoaders: 1}},
                        {loader: 'postcss-loader', options: {sourceMap: !isProd}},
                        {loader: 'less-loader', options: {sourceMap: !isProd}},
                    ]
                },
            ]
        },
        plugins: [
            new MiniCssExtractPlugin({
                filename: '[name].css',
                chunkFilename: './css/[id].css'
            }),
        ],
    };
};
