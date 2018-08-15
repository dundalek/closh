/*!
 * deasync
 * https://github.com/abbr/deasync
 *
 * Copyright 2014-2015 Abbr
 * Released under the MIT license
 */




(function () {
    
    var fs = require('fs'),
        os = require('os'),
        path = require('path'),
        zlib = require('zlib'),
        binding;

    function mkDirByPathSync(targetDir, { isRelativeToScript = false } = {}) {
        const sep = path.sep;
        const initDir = path.isAbsolute(targetDir) ? sep : '';
        const baseDir = isRelativeToScript ? __dirname : '.';

        return targetDir.split(sep).reduce((parentDir, childDir) => {
            const curDir = path.resolve(baseDir, parentDir, childDir);
            try {
                fs.mkdirSync(curDir);
            } catch (err) {
                if (err.code === 'EEXIST') { // curDir already exists!
                    return curDir;
                }

                // To avoid `EISDIR` error on Mac and `EACCES`-->`ENOENT` and `EPERM` on Windows.
                if (err.code === 'ENOENT') { // Throw the original parentDir error on curDir `ENOENT` failure.
                    throw new Error(`EACCES: permission denied, mkdir '${parentDir}'`);
                }

                const caughtErr = ['EACCES', 'EPERM', 'EISDIR'].indexOf(err.code) > -1;
                if (!caughtErr || caughtErr && targetDir === curDir) {
                    throw err; // Throw if it's just the last created dir.
                }
            }

            return curDir;
        }, initDir);
    }
    
    // Seed random numbers [gh-82] if on Windows. See https://github.com/laverdet/node-fibers/issues/82
    if(process.platform === 'win32') Math.random();
    
    
    // Look for binary for this platform
    var nodeV = 'node-9';
    var modPath = path.join(os.homedir(), '.closh', 'deasync', process.platform + '-' + process.arch + '-' + nodeV, 'deasync.node');

    try{
	fs.statSync(modPath);
    }
    catch(ex){
        let bundledPath = process.platform + '-' + process.arch + '-' + nodeV + '/' + 'deasync.node';
        let bundledPathRegex = new RegExp(bundledPath + '$', 'i');
        let embedded_files = lumo.internal.embedded.keys();
        let embedded_binary_path = embedded_files.filter(function(item){
            return bundledPathRegex.test(item);
        });
        let buffered_binary = lumo.internal.embedded.get(embedded_binary_path[0]);
        let deflated_binary = zlib.inflateSync(buffered_binary);

        // Write dir in .closh if not existing
        mkDirByPathSync(path.dirname(modPath));
        // Write the binary buffer to disk
        fs.writeFileSync(modPath, deflated_binary, "binary", function(err) {
            if(err) {
                console.error(err);
                process.exit(-1);
            }
        });
    }

    binding = require(modPath);

    function deasync(fn) {
	return function() {
	    var done = false;
	    var args = Array.prototype.slice.apply(arguments).concat(cb);
	    var err;
	    var res;

	    fn.apply(this, args);
	    module.exports.loopWhile(function(){return !done;});
	    if (err)
		throw err;

	    return res;

	    function cb(e, r) {
		err = e;
		res = r;
		done = true;		
	    }
	}
    }
    
    module.exports = deasync;
    
    module.exports.sleep = deasync(function(timeout, done) {
	setTimeout(done, timeout);
    });
    
    module.exports.runLoopOnce = function(){
	process._tickCallback();
	binding.run();
    };
    
    module.exports.loopWhile = function(pred){
	while(pred()){
	    process._tickCallback();
	    if(pred()) binding.run();
	}
    };

}());
