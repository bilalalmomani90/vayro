// يطلب الإعداد من التطبيق مع كل تحميل صفحة رئيسية، ويلغي طلبات الصور عند تفعيل الوضع الصارم.
// عند أي فشل في التواصل مع التطبيق نعود إلى "لا حجب" كي لا نكسر التصفح.
const FALLBACK = { blockImages: false, exceptions: [] };

function loadConfig() {
  return browser.runtime.sendNativeMessage("browser", { type: "config" })
    .then((c) => c || FALLBACK)
    .catch(() => FALLBACK);
}

let configPromise = loadConfig();

function hostOf(url) {
  try { return new URL(url).hostname.replace(/^www\./, ""); } catch (e) { return ""; }
}

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    if (details.type === "main_frame") {
      configPromise = loadConfig(); // تحديث الإعداد قبل تحميل عناصر الصفحة
      return {};
    }
    return configPromise.then((cfg) => {
      if (!cfg.blockImages) return {};
      const page = hostOf(details.documentUrl || details.originUrl || "");
      if (page && Array.isArray(cfg.exceptions) && cfg.exceptions.indexOf(page) !== -1) return {};
      return { cancel: true };
    });
  },
  { urls: ["<all_urls>"], types: ["main_frame", "image", "imageset"] },
  ["blocking"]
);
