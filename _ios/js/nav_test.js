(() => {
  uni.redirectTo({
    url: "/pages/bank/form?id=0&item_id=2",
    success: () => console.log("nav ok"),
    fail: (e) => console.log("nav fail " + JSON.stringify(e))
  });
  return "requested";
})()
