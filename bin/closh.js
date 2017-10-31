#!/usr/bin/env node
'use strict';
var spawn = require('child_process').spawn;
var path = require('path');
var os = require('os');
var fs = require('fs');

var bin = 'lumo';
var args = [
  '--classpath', path.join(__dirname, '..', 'src'),
  '--cache', path.join(os.homedir(), '.lumo_cache'),
  '-m', 'closh.main',
];


// NODE_PATH seems to be missing when running as global binary
var modules_path = path.join(__dirname, '..', 'node_modules');
process.env.NODE_PATH = process.env.NODE_PATH ? modules_path + ':' + process.env.NODE_PATH : modules_path;

spawn(bin, args, { stdio: 'inherit' }).on('exit', process.exit);
