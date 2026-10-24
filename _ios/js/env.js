(() => {
  const out = {
    uni: typeof uni,
    uniKeys: typeof uni === "object" ? Object.keys(uni).slice(0, 60) : null,
    navigateBack: typeof uni !== "undefined" ? typeof uni.navigateBack : null,
    navigateTo: typeof uni !== "undefined" ? typeof uni.navigateTo : null,
    getCurrentPages: typeof getCurrentPages,
    hash: location.hash,
    search: location.search,
    title: document.title
  };
  try {
    out.pages = getCurrentPages().map(p => ({ route: p.route, options: p.options }));
  } catch (e) {
    out.pagesErr = String(e);
  }
  return out;
})()
