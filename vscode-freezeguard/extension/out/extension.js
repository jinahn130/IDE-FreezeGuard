"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.deactivate = exports.activate = void 0;
const vscode = require("vscode");
const mainThreadMonitor_1 = require("./mainThreadMonitor");
const badBlocking_1 = require("./actions/badBlocking");
const backgroundFix_1 = require("./actions/backgroundFix");
const freezeGuard_1 = require("./actions/freezeGuard");
function activate(context) {
    console.log('VSCode FreezeGuard extension activated');
    // Start the main thread monitor
    mainThreadMonitor_1.monitor.start();
    // Register commands
    const measureCommand = vscode.commands.registerCommand('freezeguard.measure', freezeGuard_1.freezeGuardAction);
    const badBlockingCommand = vscode.commands.registerCommand('freezeguard.badBlocking', badBlocking_1.badBlockingAction);
    const backgroundFixCommand = vscode.commands.registerCommand('freezeguard.backgroundFix', backgroundFix_1.backgroundFixAction);
    // Add commands to context
    context.subscriptions.push(measureCommand);
    context.subscriptions.push(badBlockingCommand);
    context.subscriptions.push(backgroundFixCommand);
    console.log('FreezeGuard commands registered and monitor started');
}
exports.activate = activate;
function deactivate() {
    console.log('VSCode FreezeGuard extension deactivated');
    mainThreadMonitor_1.monitor.stop();
}
exports.deactivate = deactivate;
//# sourceMappingURL=extension.js.map