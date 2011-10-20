package play.api.mvc {

  import play.api._
  import play.api.libs.iteratee._

  import scala.annotation._

  /**
   * The HTTP request header. Note that it doesn't contain the request body yet.
   */
  @implicitNotFound("Cannot find any HTTP Request Header here")
  trait RequestHeader {

    /**
     * The complete request URI (contains both path and query string).
     */
    def uri: String

    /**
     * The URI path.
     */
    def path: String

    /**
     * The HTTP Method.
     */
    def method: String

    /**
     * The parsed query string.
     */
    def queryString: Map[String, Seq[String]]

    /**
     * The HTTP headers.
     */
    def headers: Headers

    /**
     * The HTTP cookies.
     */
    def cookies: Cookies

    /**
     * The username if defined for this request.
     * It is usually set by wrapping your Action in another Action like Authenticated.
     *
     * Example:
     * {{{
     * Authenticated {
     *   Action { request =>
     *     Ok(request.username.map("Hello " + _))
     *   }
     * }
     * }}}
     *
     * @see Authenticated
     */
    def username: Option[String]

    /**
     * Parse the Session cookie and returns the Session data.
     */
    lazy val session: Session = Session.decodeFromCookie(cookies.get(Session.SESSION_COOKIE_NAME))

    /**
     * Parse the Flash cookie and returns the Flash data.
     */
    lazy val flash: Flash = Flash.decodeFromCookie(cookies.get(Flash.FLASH_COOKIE_NAME))

    /**
     * Return the raw query string.
     */
    lazy val rawQueryString = uri.split('?').drop(1).mkString("?")

    override def toString = {
      method + " " + uri
    }

  }

  /**
   * The complete HTTP request.
   *
   * @tparam A The body content type.
   */
  @implicitNotFound("Cannot find any HTTP Request here")
  trait Request[+A] extends RequestHeader {

    /**
     * The body content.
     */
    def body: A

  }

  /**
   * The HTTP response.
   */
  @implicitNotFound("Cannot find any HTTP Response here")
  trait Response {

    /**
     * Handle a result.
     *
     * Depending of the result type, it will be sent synchronously or asynchronously.
     */
    def handle(result: Result): Unit

  }

  /**
   * Define a Call, describing an HTTP request. For example used to create links or fill redirect data.
   *
   * These values are usually generated by the reverse router.
   */
  case class Call(method: String, url: String) extends play.mvc.Call {
    override def toString = url
  }

  /**
   * HTTP headers set.
   */
  trait Headers {

    /**
     * Optionally returns the first header value associated with a key.
     */
    def get(key: String): Option[String] = getAll(key).headOption

    /**
     * Retrieves the first header value which is associated with the given key.
     */
    def apply(key: String): String = get(key).getOrElse(scala.sys.error("Header doesn't exist"))

    /**
     * Retrieve all header values associated with the given key.
     */
    def getAll(key: String): Seq[String]

  }

  /**
   * HTTP Session.
   *
   * Session data are encoded into an HTTP cookie, and can only contain simple String values.
   */
  case class Session(data: Map[String, String] = Map.empty[String, String]) {

    /**
     * Optionally returns the session value associated with a key.
     */
    def get(key: String) = data.get(key)

    /**
     * Is this session empty?
     */
    def isEmpty: Boolean = data.isEmpty

    /**
     * Add value to a session, and return a new session.
     *
     * Example:
     * {{{
     * session + ("username" -> "bob")
     * }}}
     *
     * @param kv The key-value pair to add.
     * @return The modified session.
     */
    def +(kv: (String, String)) = copy(data + kv)

    /**
     * Remove any value from the session.
     *
     * Example:
     * {{{
     * session - "username"
     * }}}
     *
     * @param key The key to remove.
     * @return The modified session.
     */
    def -(key: String) = copy(data - key)

    /**
     * Retrieves the session value which is associated with the given key.
     */
    def apply(key: String) = data(key)

  }

  /**
   * Helper utilities to manage the Session cookie.
   */
  object Session {

    /**
     * The session cookie name.
     */
    val SESSION_COOKIE_NAME = "PLAY_SESSION"

    /**
     * A blank (empty) session.
     */
    val blankSession = new Session

    /**
     * Encode the session data as String.
     */
    def encode(session: Session): String = {
      java.net.URLEncoder.encode(session.data.filterNot(_._1.contains(":")).map(d => d._1 + ":" + d._2).mkString("\u0000"))
    }

    /**
     * Decode a session from an encoded String.
     */
    def decode(data: String): Session = {
      try {
        Option(data.trim).filterNot(_.isEmpty).map { data =>
          Session(java.net.URLDecoder.decode(data).split("\u0000").map(_.split(":")).map(p => p(0) -> p.drop(1).mkString(":")).toMap)
        }.getOrElse(blankSession)
      } catch {
        // fail gracefully is the session cookie is corrupted
        case _ => blankSession
      }
    }

    /**
     * Encode the session data as a Cookie.
     */
    def encodeAsCookie(data: Session): Cookie = {
      Cookie(SESSION_COOKIE_NAME, encode(data))
    }

    /**
     * Decode the session data from a Cookie.
     */
    def decodeFromCookie(sessionCookie: Option[Cookie]): Session = {
      sessionCookie.filter(_.name == SESSION_COOKIE_NAME).map(c => decode(c.value)).getOrElse(blankSession)
    }

  }

  /**
   * HTTP Flash.
   *
   * Flash data are encoded into an HTTP cookie, and can only contain simple String values.
   */
  case class Flash(data: Map[String, String] = Map.empty[String, String]) {

    /**
     * Optionally returns the flash value associated with a key.
     */
    def get(key: String) = data.get(key)

    /**
     * Is this flash scope empty?
     */
    def isEmpty: Boolean = data.isEmpty

    /**
     * Add value to the flash scope, and return a new flash.
     *
     * Example:
     * {{{
     * flash + ("success" -> "Done!")
     * }}}
     *
     * @param kv The key-value pair to add.
     * @return The modified flash scope.
     */
    def +(kv: (String, String)) = copy(data + kv)

    /**
     * Remove any value from the flash scope.
     *
     * Example:
     * {{{
     * flash - "success"
     * }}}
     *
     * @param key The key to remove.
     * @return The modified flash scope.
     */
    def -(key: String) = copy(data - key)

    /**
     * Retrieves the flash value which is associated with the given key.
     */
    def apply(key: String) = data(key)

  }

  /**
   * Helper utilities to manage the Flash cookie.
   */
  object Flash {

    /**
     * The flash cookie name.
     */
    val FLASH_COOKIE_NAME = "PLAY_FLASH"

    /**
     * A blank (empty) flash scope.
     */
    val blankFlash = new Flash

    /**
     * Encode the flash data as String.
     */
    def encode(flash: Flash): String = {
      java.net.URLEncoder.encode(flash.data.filterNot(_._1.contains(":")).map(d => d._1 + ":" + d._2).mkString("\u0000"))
    }

    /**
     * Decode a flash scope from an encoded String.
     */
    def decode(data: String): Flash = {
      try {
        Option(data.trim).filterNot(_.isEmpty).map { data =>
          Flash(java.net.URLDecoder.decode(data).split("\u0000").map(_.split(":")).map(p => p(0) -> p.drop(1).mkString(":")).toMap)
        }.getOrElse(blankFlash)
      } catch {
        // fail gracefully is the flash cookie is corrupted
        case _ => blankFlash
      }
    }

    /**
     * Encode the flash data as a Cookie.
     */
    def encodeAsCookie(data: Flash): Cookie = {
      Cookie(FLASH_COOKIE_NAME, encode(data))
    }

    /**
     * Decode the flash data from a Cookie.
     */
    def decodeFromCookie(flashCookie: Option[Cookie]): Flash = {
      flashCookie.filter(_.name == FLASH_COOKIE_NAME).map(c => decode(c.value)).getOrElse(blankFlash)
    }

  }

  /**
   * An HTTP cookie.
   *
   * @param name The cookie name.
   * @param value The cookie value.
   * @param maxAge The cookie expiration date in seconds (-1 for a transient cookie, 0 for a cookie that expires now)
   * @param path The cookie path (default to root path /)
   * @param domain The cookie domain.
   * @param secure Is this cookie secured? (sent only for HTTPS requests)
   * @param httpOnly Is this cookie HTTP only? (not accessible from client side javascipt code)
   */
  case class Cookie(name: String, value: String, maxAge: Int = -1, path: String = "/", domain: Option[String] = None, secure: Boolean = false, httpOnly: Boolean = true)

  /**
   * HTTP cookies set.
   */
  trait Cookies {

    /**
     * Optionally returns the cookie associated with a key.
     */
    def get(name: String): Option[Cookie]

    /**
     * Retrieves the cookie which is associated with the given key.
     */
    def apply(name: String): Cookie = get(name).getOrElse(scala.sys.error("Cookie doesn't exist"))

  }

  /**
   * Helper utilities to encode Cookies.
   */
  object Cookies {

    import scala.collection.JavaConverters._

    // We use netty here but just as an API to handle cookies encoding
    import org.jboss.netty.handler.codec.http.{ CookieEncoder, CookieDecoder, DefaultCookie }

    /**
     * Encode cookies as a proper HTTP header.
     *
     * @param cookies The Cookies to encode.
     * @param discard Discard these cookies as well.
     * @return A valid Set-Cookie header value.
     */
    def encode(cookies: Seq[Cookie], discard: Seq[String] = Nil): String = {
      val encoder = new CookieEncoder(true)
      cookies.foreach { c =>
        encoder.addCookie {
          val nc = new DefaultCookie(c.name, c.value)
          nc.setMaxAge(c.maxAge)
          nc.setPath(c.path)
          c.domain.map(nc.setDomain(_))
          nc.setSecure(c.secure)
          nc.setHttpOnly(c.httpOnly)
          nc
        }
      }
      discard.foreach { n =>
        encoder.addCookie {
          val nc = new DefaultCookie(n, "")
          nc.setMaxAge(0)
          nc
        }
      }
      encoder.encode()
    }

    /**
     * Decode a Set-Cookie header value as a proper cookie set.
     *
     * @param cookieHeader The Set-Cookie header value.
     * @return Decoded cookies.
     */
    def decode(cookieHeader: String): Seq[Cookie] = {
      new CookieDecoder().decode(cookieHeader).asScala.map { c =>
        Cookie(c.getName, c.getValue, c.getMaxAge, Option(c.getPath).getOrElse("/"), Option(c.getDomain), c.isSecure, c.isHttpOnly)
      }.toSeq
    }

    /**
     * Merge an existing Set-Cookie header with new cookies values.
     *
     * @param cookieHeader The existing Set-Cookie header value.
     * @param cookies The new cookies to encode.
     * @param discard Discard these cookies as well.
     * @return A valid Set-Cookie header value.
     */
    def merge(cookieHeader: String, cookies: Seq[Cookie], discard: Seq[String] = Nil): String = {
      encode(decode(cookieHeader) ++ cookies, discard)
    }

  }

}
