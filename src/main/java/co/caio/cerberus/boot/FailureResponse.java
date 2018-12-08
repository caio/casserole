package co.caio.cerberus.boot;

class FailureResponse extends BaseResponse {
  public final String message;
  public final ErrorKind error;

  @Override
  public boolean isSuccess() {
    return false;
  }

  private FailureResponse(ErrorKind error, String message) {
    this.error = error;
    this.message = message;
  }

  static FailureResponse queryError(String message) {
    return new FailureResponse(ErrorKind.QUERY_ERROR, message);
  }

  static FailureResponse unknownError(String message) {
    return new FailureResponse(ErrorKind.UNKNOWN_ERROR, message);
  }

  enum ErrorKind {
    QUERY_ERROR,
    UNKNOWN_ERROR,
  }
}
