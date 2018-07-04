const version = require("../package.json").engines.node.replace(/\.x$/, '');

if (!process.version.startsWith("v" + version + ".")) {
  console.log("ERROR: Closh requires node version " + version + ", but your version is " + process.version + ".\n");
  console.log("Please switch to the required node version and install closh again.\n");
  console.log("To switch node versions you can use nvm, learn more at https://github.com/creationix/nvm\n");
  console.log("Once you have nvm you can install the proper node version with:\n");
  console.log("$ nvm install v" + version + " && nvm use v" + version + "\n");
  process.exit(1);
}
