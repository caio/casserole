package co.caio.cerberus.boot;

import co.caio.cerberus.model.SearchResult;

class SuccessResponse extends BaseResponse {
  public SearchResult result;

  @Override
  public boolean isSuccess() {
    return true;
  }

  SuccessResponse(SearchResult result) {
    this.result = result;
  }

  // For testing
  private SuccessResponse() {}

  private void setResult(SearchResult result) {
    this.result = result;
  }
}
