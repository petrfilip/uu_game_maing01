const HttpClient = {
  get(url, successFn, failureFn) {
    return new Promise((resolve, reject) => {
      let xhr = new XMLHttpRequest();
      xhr.open("GET", url, true);
      xhr.withCredentials = true;
      xhr.setRequestHeader("Accept", "application/json");
      xhr.onreadystatechange = (/*e*/) => {
        if (xhr.readyState == 4) {
          if (xhr.status >= 200 && xhr.status < 300) {
            let json;
            try {
              json = xhr.responseText ? JSON.parse(xhr.responseText) : null;
            } catch (err) {
              reject(err);
              if (typeof failureFn === "function") failureFn(err);
              return;
            }
            resolve(json);
            if (typeof successFn === "function") successFn(json);
          } else {
            let err = new Error("Server responded with status " + xhr.status);
            reject(err);
            if (typeof failureFn === "function") failureFn(err);
          }
        }
      };
      xhr.send(null);
    });
  },
};

export default HttpClient;
