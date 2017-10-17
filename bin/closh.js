#!/usr/bin/env node
'use strict';
var spawn = require('child_process').spawn;
var path = require('path');

var bin = 'lumo';
var args = [
  '--classpath', path.join(__dirname, '../src'),
  '--dependencies', 'alter-cljs:0.2.0',
  path.join(__dirname, '../src/closh/main.cljs')
];

spawn(bin, args, { stdio: 'inherit' }).on('exit', process.exit);
