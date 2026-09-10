import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { promptAction } from '@kit.ArkUI';
import { KRAny } from '@kuikly-open/render';
import { KuiklyRenderBaseModule } from '@kuikly-open/render';
import { KuiklyRenderCallback } from '@kuikly-open/render';
import util from '@ohos.util';
import fs from '@ohos.file.fs';
import { fileUri } from '@kit.CoreFileKit';
import { ThemeController } from '../ThemeController';

export class KRBridgeModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = "HRBridgeModule";
  static readonly SHOW_ALERT = "showAlert";
  static readonly CLOSE_PAGE = "closePage";
  static readonly OPEN_PAGE = "openPage";
  static readonly COPY_TO_PASTE_BOARD = "copyToPasteboard";
  static readonly TOAST = "toast";
  static readonly LOG = "log";
  static readonly REPORT_DT = "reportDT";
  static readonly REPORT_REAL_TIME = "reportRealtime";
  static readonly LOCAL_SERVE_TIME = "localServeTime";
  static readonly CURRENT_TIME_STAMP = "currentTimestamp";
  static readonly DATE_FORMATTER = "dateFormatter";
  static readonly SET_THEME_MODE = "setThemeMode";
  static readonly READ_ASSET_FILE = "readAssetFile";

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback): KRAny {
    switch (method) {
      case KRBridgeModule.SHOW_ALERT: {
        this.showAlert(params, callback);
        break;
      }
      case KRBridgeModule.CLOSE_PAGE: {
        this.closePage();
        break;
      }
      case KRBridgeModule.OPEN_PAGE: {
        this.openPage();
        break;
      }
      case KRBridgeModule.COPY_TO_PASTE_BOARD: {
        this.copyToPasteboard();
        break;
      }
      case KRBridgeModule.TOAST: {
        this.toast(params);
        break;
      }
      case KRBridgeModule.LOG: {
        this.log(params);
        break;
      }
      case KRBridgeModule.REPORT_DT: {
        this.reportDT(params);
        break;
      }
      case KRBridgeModule.REPORT_REAL_TIME: {
        this.reportRealtime(params);
        break;
      }
      case KRBridgeModule.LOCAL_SERVE_TIME: {
        this.localServeTime(params, callback);
        break;
      }
      case KRBridgeModule.CURRENT_TIME_STAMP: {
        return this.currentTimestamp();
      }
      case KRBridgeModule.DATE_FORMATTER: {
        return this.dateFormatter(params);
      }
      case KRBridgeModule.SET_THEME_MODE: {
        this.setThemeMode(params);
        break;
      }
      case KRBridgeModule.READ_ASSET_FILE: {
        this.readAssetFile(params, callback);
        break;
      }
      default:
        break;
    }
    return null;
  }

  private showAlert(params: KRAny, callback: KuiklyRenderCallback) {
  }

  // TODO close page
  private closePage() {
  }

  // TODO open new page
  private openPage() {
  }

  // TODO copy to paste board
  private copyToPasteboard() {
  }

  private toast(params: KRAny) {
    let content: string = '';
    try {
      const record: Record<string, string> = JSON.parse(params as string) as Record<string, string>;
      content = record['content'] ?? '';
    } catch (e) {
      content = '';
    }
    if (content.length === 0) {
      return;
    }
    try {
      const uiContext: UIContext | undefined = AppStorage.get<UIContext>('context');
      if (uiContext) {
        uiContext.getPromptAction().showToast({ message: content, duration: 2000 });
        return;
      }
      promptAction.showToast({ message: content, duration: 2000 });
    } catch (e) {
      console.error(`showToast failed: ${JSON.stringify(e)}`);
    }
  }

  private log(params: KRAny) {
    if (params == null) {
      return
    }
    console.log(`KuiklyRender ${JSON.stringify(params)}`,)
  }

  private reportDT(params: KRAny) {
  }

  private reportRealtime(params: KRAny) {
  }

  private localServeTime(params: KRAny, callback: KuiklyRenderCallback) {
    callback({ "time": Date.now() / 1000.0 });
  }

  private currentTimestamp(): string {
    return Date.now().toString()
  }

  /**
   * 日期格式化：入参 { timeStamp: 毫秒时间戳, format: "HH:mm:ss" }
   * 业务当前只用到 HH:mm:ss（列表更新时间），其余按需扩展。
   */
  private dateFormatter(params: KRAny): string {
    try {
      const record: Record<string, Object> = JSON.parse(params as string) as Record<string, Object>;
      const timeStamp: number = Number(record['timeStamp'] ?? 0);
      const format: string = (record['format'] as string) ?? 'HH:mm:ss';
      const date: Date = new Date(timeStamp);
      const p2 = (n: number): string => (n < 10 ? `0${n}` : `${n}`);
      return format
        .replace('yyyy', `${date.getFullYear()}`)
        .replace('MM', p2(date.getMonth() + 1))
        .replace('dd', p2(date.getDate()))
        .replace('HH', p2(date.getHours()))
        .replace('mm', p2(date.getMinutes()))
        .replace('ss', p2(date.getSeconds()));
    } catch (e) {
      return '';
    }
  }

  /** 切换主题模式：params 为 { mode: 0跟随系统/1浅色/2深色 } */
  private setThemeMode(params: KRAny): void {
    let mode: number = ThemeController.MODE_AUTO;
    try {
      const record: Record<string, Object> = JSON.parse(params as string) as Record<string, Object>;
      mode = Number(record['mode'] ?? ThemeController.MODE_AUTO);
    } catch (e) {
      mode = ThemeController.MODE_AUTO;
    }
    ThemeController.setMode(mode);
  }

  private readAssetFile(params: KRAny, callback: KuiklyRenderCallback) {
    // let startTimeMills = Date.now();
    let records = JSON.parse(params as string) as Record<string, string>;
    let assetPath = records["assetPath"];

    let context = getContext(this) as common.UIAbilityContext;
    /*
    context.resourceManager.getRawFileContent(assetPath, (error: BusinessError, value: Uint8Array) => {
      let textDecoder: util.TextDecoder = util.TextDecoder.create();
      let res: string = textDecoder.decodeWithStream(value);
      callback({
        "result": JSON.parse(res)
      });
    });
    */
    let resDir = context.resourceDir
    let path = resDir + '/' + assetPath
    let assetURI = fileUri.getUriFromPath(path)
    const file = fs.openSync(assetURI, fs.OpenMode.READ_ONLY);
    const stat = fs.statSync(file.fd);
    const arrayBuffer = new ArrayBuffer(stat.size);
    fs.read(file.fd, arrayBuffer, (err: BusinessError, readLen: number) => {
      if (err) {
        console.error("read failed with error message: " + err.message + ", error code: " + err.code);
      } else {
        let buf = new Uint8Array(arrayBuffer, 0, readLen);
        const textDecoder = new util.TextDecoder('utf-8');
        const res = textDecoder.decodeWithStream(new Uint8Array(buf));
        callback({
          'result': JSON.parse(res)
        });
      }
      fs.closeSync(file);
    });
  }
}