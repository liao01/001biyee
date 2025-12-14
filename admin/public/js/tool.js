Tool = {
  /**
   * 空校验 null或""都返回true
   */
  isEmpty: (obj) => {
    if ((typeof obj === 'string')) {
      return !obj || obj.replace(/\s+/g, "") === ""
    } else {
      return (!obj || JSON.stringify(obj) === "{}" || obj.length === 0);
    }
  },

  /**
   * 非空校验
   */
  isNotEmpty: (obj) => {
    return !Tool.isEmpty(obj);
  },

  /**
   * 对象复制
   * @param obj
   */
  copy: (obj) => {
    if (Tool.isNotEmpty(obj)) {
      return JSON.parse(JSON.stringify(obj));
    }
  },

  /**
   * 使用递归将数组转为树形结构
   * 父ID属性为parent
   */
  array2Tree: (array, parentId) => {
    if (Tool.isEmpty(array)) {
      return [];
    }

    const result = [];
    for (let i = 0; i < array.length; i++) {
      const c = array[i];
      // console.log(Number(c.parent), Number(parentId));
      if (Number(c.parent) === Number(parentId)) {
        result.push(c);

        // 递归查看当前节点对应的子节点
        const children = Tool.array2Tree(array, c.id);
        if (Tool.isNotEmpty(children)) {
          c.children = children;
        }
      }
    }
    return result;
  },

  /**
   * 随机生成[len]长度的[radix]进制数
   * @param len
   * @param radix 默认62
   * @returns {string}
   */
  uuid: (len, radix = 62) => {
    const chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'.split('');
    const uuid = [];
    radix = radix || chars.length;

    for (let i = 0; i < len; i++) {
      uuid[i] = chars[0 | Math.random() * radix];
    }

    return uuid.join('');
  },

  /**
   * 时长格式化
   * @param value 例如：36000
   * @returns {string} 例如：10:00:00
   */
  formatSecond: (value, showEmptyHour = false) => {
    value = value || 0;
    let second = parseInt(value, 10); // 秒
    let minute = 0; // 分
    let hour = 0; // 小时
    if (second >= 60) {
      // 当大于60秒时，才需要做转换
      minute = Math.floor(second / 60);
      second = Math.floor(second % 60);
      if (minute >= 60) {
        hour = Math.floor(minute / 60);
        minute = Math.floor(minute % 60);
      }
    } else {
      // 小于60秒时，直接显示，不需要处理
    }
    let result = "" + Tool.prefixInteger(second, 2) + "";
    // 拼上分钟
    result = "" + Tool.prefixInteger(minute, 2) + ":" + result;
    if (showEmptyHour) {
      // 拼上小时
      result = "" + Tool.prefixInteger(hour, 2) + ":" + result;
    } else if ( hour > 0) {
      // 拼上小时
      result = "" + (hour < 10 ? Tool.prefixInteger(hour, 2) : hour) + ":" + result;
    }
    return result;
  },

  /**
   * 格式化指定长度，前面补0
   */
  prefixInteger: (num, length) => {
    return (Array(length).join('0') + num).slice(-length);
  }
};
