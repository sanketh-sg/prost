const { resolve } = require('path');
const WatchExternalFilesPlugin = require('webpack-watch-files-plugin').default;
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CssMinimizerPlugin = require('css-minimizer-webpack-plugin');
const assets = 'src/main/resources/static/build';

module.exports = (_, argv) => ({
    // Driven by webpack's own --mode flag, so `pnpm run build:prod` needs no
    // NODE_ENV prefix (which does not work in PowerShell) and no cross-env.
    mode: argv.mode ?? 'development',
    devtool: argv.mode === 'production' ? false : 'source-map',
    entry: {
        bundle: {
            import: './frontend/index.js',
            library: {
                name: 'prostLib',
                type: 'var',
            },
        },
        summary_page: './frontend/summary_page/index.js',
        admin: './frontend/admin/index.js',
    },
    output: {
        path: resolve(__dirname, assets),
        publicPath: '/build/',
        filename: '[name].js',
        // Without this, source maps from a dev build survive a production build
        // and get packaged into the jar.
        clean: true,
    },
    plugins: [
        new MiniCssExtractPlugin({
            filename: '[name].css',
        }),
        new WatchExternalFilesPlugin({
            files: ['src/main/resources/templates/**/*.html'],
        }),
        new CssMinimizerPlugin(),
    ],
    module: {
        rules: [
            {
                test: /\.css$/i,
                include: resolve(__dirname, 'frontend'),
                use: [
                    MiniCssExtractPlugin.loader,
                    'css-loader',
                    'postcss-loader',
                ],
                sideEffects: true,
            },
        ],
    },
});
