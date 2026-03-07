import { definePlugin } from "@halo-dev/ui-shared";
import { markRaw } from "vue";
import PlatformList from "./views/PlatformList.vue";
import TaskList from "./views/TaskList.vue";
import PlatformForm from "./views/PlatformForm.vue";

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/multi-publisher",
        name: "MultiPublisher",
        redirect: "/multi-publisher/platforms",
        meta: {
          title: "多平台同步",
          menu: {
            name: "多平台同步",
            group: "content",
            icon: markRaw({ 
              template: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M13.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM9.8 8.9L7 23h2.1l1.8-8 2.1 2v6h2v-7.5l-2.1-2 .6-3C14.8 12 16.8 13 19 13v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1L6 8.3V13h2V9.6l1.8-.7"/></svg>' 
            }),
            priority: 0
          }
        },
        children: [
          {
            path: "platforms",
            name: "PublisherPlatforms",
            component: PlatformList,
            meta: {
              title: "平台管理"
            }
          },
          {
            path: "platforms/create",
            name: "PublisherPlatformCreate",
            component: PlatformForm,
            meta: {
              title: "添加平台"
            }
          },
          {
            path: "platforms/:name/edit",
            name: "PublisherPlatformEdit",
            component: PlatformForm,
            meta: {
              title: "编辑平台"
            }
          },
          {
            path: "tasks",
            name: "PublisherTasks",
            component: TaskList,
            meta: {
              title: "同步任务"
            }
          }
        ]
      }
    }
  ],
  extensionPoints: {}
});
