package co.caio.cerberus.boot;

class FailureResponse extends BaseResponse {
  public final String path;
  public final String message;
  public final ErrorKind error;

  @Override
  public boolean isSuccess() {
    return false;
  }

  FailureResponse(ErrorKind error, String requestPath, String message) {
    this.error = error;
    this.path = requestPath;
    this.message = message;
  }

  public static FailureResponse queryError(String path, String message) {
    return new FailureResponse(ErrorKind.QUERY_ERROR, path, message);
  }

  public static FailureResponse unknownError(String path, String message) {
    return new FailureResponse(ErrorKind.UNKNOWN_ERROR, path, message);
  }

  enum ErrorKind {
    QUERY_ERROR,
    UNKNOWN_ERROR,
  }
}
