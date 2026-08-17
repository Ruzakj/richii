import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.ricspace.app",
  appName: "Ric Space",
  webDir: "www",
  server: {
    url: "https://vibetube-cloud.vercel.app",
    cleartext: false,
    allowNavigation: ["vibetube-cloud.vercel.app", "*.vercel.app"]
  },
  android: {
    backgroundColor: "#05080d"
  }
};

export default config;
