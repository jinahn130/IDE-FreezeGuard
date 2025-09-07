import * as vscode from 'vscode';
import { monitor } from './mainThreadMonitor';
import { badBlockingAction } from './actions/badBlocking';
import { backgroundFixAction } from './actions/backgroundFix';
import { freezeGuardAction } from './actions/freezeGuard';

export function activate(context: vscode.ExtensionContext) {
  console.log('VSCode FreezeGuard extension activated');
  
  // Start the main thread monitor
  monitor.start();

  // Register commands
  const measureCommand = vscode.commands.registerCommand('freezeguard.measure', freezeGuardAction);
  const badBlockingCommand = vscode.commands.registerCommand('freezeguard.badBlocking', badBlockingAction);
  const backgroundFixCommand = vscode.commands.registerCommand('freezeguard.backgroundFix', backgroundFixAction);

  // Add commands to context
  context.subscriptions.push(measureCommand);
  context.subscriptions.push(badBlockingCommand);
  context.subscriptions.push(backgroundFixCommand);

  console.log('FreezeGuard commands registered and monitor started');
}

export function deactivate() {
  console.log('VSCode FreezeGuard extension deactivated');
  monitor.stop();
}