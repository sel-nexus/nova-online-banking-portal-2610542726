/** Run browser component tests in bounded headless Chromium. */
/** Use a short native temporary path so Chromium's Unix socket fits. */
process.env.TMPDIR='/tmp';
process.env.CHROME_BIN ||= require('playwright').chromium.executablePath();
module.exports = function(config) { config.set({frameworks:['jasmine','@angular-devkit/build-angular'],plugins:[require('karma-jasmine'),require('karma-chrome-launcher'),require('karma-coverage'),require('@angular-devkit/build-angular/plugins/karma')],reporters:['progress'],browsers:['ChromiumHeadless'],customLaunchers:{ChromiumHeadless:{base:'ChromeHeadless',flags:['--no-sandbox','--disable-dev-shm-usage']}},singleRun:true,browserNoActivityTimeout:60000}); };

