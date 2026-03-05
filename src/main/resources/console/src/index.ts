import { definePlugin } from "@halo-dev/console-shared";
import PlatformList from "./PlatformList.vue";
import PlatformForm from "./PlatformForm.vue";

export default definePlugin({
  components: {},
  routes: [
    {
      path: "/sync-platforms",
      component: PlatformList,
      meta: {
        title: "多平台同步",
        menu: {
          name: "sync-platforms",
          title: "多平台同步",
          icon: "ri:share-circle-line",
          link: "/sync-platforms",
        },
      },
    },
    {
      path: "/sync-platforms/create",
      component: PlatformForm,
      meta: {
        title: "添加平台",
      },
    },
    {
      path: "/sync-platforms/:name/edit",
      component: PlatformForm,
      meta: {
        title: "编辑平台",
      },
    },
  ],
  extensionPoints: {
    "post:list:action:create": [
    {
      type: "dropdown",
      primary: true,
      label: "同步到其他平台",
      icon: "ri:share-circle-line",
      action: () => {
        // TODO: 打开同步对话框
        console.log("Sync post:", post);
      },
    },
  ],
});
