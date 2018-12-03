package co.caio.cerberus.boot;

import co.caio.cerberus.model.SearchResult;

class SuccessResponse extends BaseResponse {
  public final SearchResult result;

  @Override
  public boolean isSuccess() {
    return true;
  }

  SuccessResponse(SearchResult result) {
    this.result = result;
  }
}
