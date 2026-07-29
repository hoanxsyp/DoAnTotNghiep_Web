import axiosClient from './axiosClient';
import { unwrap } from './apiHelper';

/** API chatbot tìm trọ [§9.3]. Công khai (khách dùng được ở mức cơ bản). */
const chatbotApi = {
  sendMessage: (payload) => unwrap(axiosClient.post('/ai/chatbot/message', payload)),
};

export default chatbotApi;
