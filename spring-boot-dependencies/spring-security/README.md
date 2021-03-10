## Spring Security入门

### 依赖与配置

#### maven依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

#### Spring Security配置

```java
@Configuration
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    
    /**
     * 自定义用户管理系统
     */
    @Bean
    public UserDetailsManager userDetailsManager() {
        UserManager userManager = new UserManager();
        userManager.createUser(innerUser());
        return userManager;
    }

    private UserDetails innerUser() {
        // load user by username 模拟从数据库获取用户权限等信息
        List<GrantedAuthority> authorities = new ArrayList<>();
        // 添加 ADMIN & USER 权限
        authorities.add(new SimpleGrantedAuthority("USER"));
        authorities.add(new SimpleGrantedAuthority("ADMIN"));
        // 一般数据库用户密码存入时会先加密，此处只是模拟加密后的用户信息
        // 使用UserDetails.User$UserBuilder构建user
        return User.withUsername("jack")
                .passwordEncoder(new BCryptPasswordEncoder()::encode)
                .password("jack") // 如果不开启加密，那么需要去除passwordEncoder，密码变成"{noop}jack"
                // AuthorityUtils.NO_AUTHORITIES
                .authorities(authorities)
                .build();
    }
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // login页面登录成功后重定向地址（如果是successfulForwardUrl则是转发）
        http.formLogin().defaultSuccessUrl("http://www.baidu.com") 
            .and().authorizeRequests()
            .antMatchers("/hello", "/json").access("hasAuthority('USER')") // SPEL表达式
            .antMatchers("/admin/**").access("hasAuthority('ADMIN') and hasAuthority('USER')")
            .antMatchers("/super/**").access("hasAuthority('SUPER_ADMIN')")
            // 使用自定义类实现校验,false就需要登录
            .antMatchers("/test").access("@rbacService.checkPermission()") 
            .antMatchers("/**").authenticated() // 只要是登录用户都可以访问（不需要查验权限之类）
            .and().csrf() // 添加csrf的支持
            // 返回json信息
            .and().exceptionHandling().accessDeniedHandler(new JsonAccessDeniedHandler()); 
        	// hasRole 和 hasAuthority的区别，前者会拼接'ROLE_'前缀，后者不会
    }
    
     @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
         auth.userDetailsService(userDetailsManager()).passwordEncoder(passwordEncoder());
    }
}

public class UserManager implements UserDetailsManager {

    private static Map<String, UserDetails> users = new HashMap<>();

    @Override
    public void createUser(UserDetails user) {
        users.putIfAbsent(user.getUsername(), user);
    }

    @Override
    public void updateUser(UserDetails user) {
        users.put(user.getUsername(), user);
    }

    @Override
    public void deleteUser(String username) {
        users.remove(username);
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (null == current) {
            throw new AccessDeniedException("Can't not change password! because no authentication found in context for current user");
        }
        String username = current.getName();
        UserDetails userDetails = users.get(username);
        if (null == userDetails) {
            throw new RuntimeException("Current user not exist in database!");
        }
        // change password
    }

    @Override
    public boolean userExists(String username) {
        return users.containsKey(username);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.get(username);
    }
}
```

---

## 用户接口与编码器

### 获取用户信息

Spring Security（简称SS）的用户信息由使用者通过实现框架提供的`UserDetailsService$loadUserByUsername()`接口方法提供。SS还提供了`UserDetails接口`，作为提供用户信息的核心接口。内嵌了`User类`作为`UserDetails`的默认实现。`UserDetailsManager`作为管理用户信息（增删改查）的默认内嵌管理器接口。而`InMemoryUserDetailsManager`则是默认的用户管理器实现。![](https://image.leejay.top/FkHsFi8_iqAaYOZ9nXECDTbk8L0d)

> User类内嵌了`UserBuilder`，用于建造设计模式的使用。
>
> `InMemoryUserDetailsManager`默认是由`UserDetailsServiceAutoConfiguration`类构造并注入IOC容器。

### PasswordEncoder编码器

```java
public interface PasswordEncoder {
    
    String encode(CharSequence rawPassword);
    
    boolean matches(CharSequence rawPassword, String encodedPassword);
    
    default boolean upgradeEncoding(String encodedPassword) {
		return false;
	}
}

public class DelegatingPasswordEncoder implements PasswordEncoder {
    public DelegatingPasswordEncoder(String idForEncode,
		Map<String, PasswordEncoder> idToPasswordEncoder) {
    }
}

public class PasswordEncoderFactories {
    public static PasswordEncoder createDelegatingPasswordEncoder() {
    	String encodingId = "bcrypt";
		Map<String, PasswordEncoder> encoders = new HashMap<>();
		encoders.put(encodingId, new BCryptPasswordEncoder());
        // 此处省略
        return new DelegatingPasswordEncoder(encodingId, encoders);
    }
}

public abstract class WebSecurityConfigurerAdapter implements
		WebSecurityConfigurer<WebSecurity> {
    static class LazyPasswordEncoder implements PasswordEncoder {
        private PasswordEncoder getPasswordEncoder() {
			// 此处省略
			if (passwordEncoder == null) {
				passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
			}
		}
    }
}
```

> 1. 所有的编码器都要实现该接口，当IOC容器中无编码器时，SS默认的编码器就是`BCrypt`。
> 2. 此处采用了`适配器`模式，交由`DelegatingPasswordEncoder`来处理默认的编码器工作。
> 3. 默认由`PasswordEncoderFactories`静态工厂生产`DelegatingPasswordEncoder`。
> 4. 静态工厂由SS默认配置接口`WebSecurityConfigurer`的适配器类实现`WebSecurityConfigurerAdapter`调用。

---

## 自动配置

### SecurityAutoConfiguration

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(DefaultAuthenticationEventPublisher.class)
@EnableConfigurationProperties(SecurityProperties.class)
@Import({ SpringBootWebSecurityConfiguration.class, WebSecurityEnablerConfiguration.class,
		SecurityDataConfiguration.class })
public class SecurityAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(AuthenticationEventPublisher.class)
	public DefaultAuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publisher) {
		return new DefaultAuthenticationEventPublisher(publisher);
	}

}
```

> 1. 默认注入`DefaultAuthenticationEventPublisher`用于时间的发布。
> 2. 注入配置类`SecurityProperties`
> 3. 注入`SpringBootWebSecurityConfiguration`、`WebSecurityEnablerConfiguration`、`SecurityDataConfiguration`

#### SpringBootWebSecurityConfiguration

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(WebSecurityConfigurerAdapter.class)
@ConditionalOnMissingBean(WebSecurityConfigurerAdapter.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
public class SpringBootWebSecurityConfiguration {

	@Configuration(proxyBeanMethods = false)
	@Order(SecurityProperties.BASIC_AUTH_ORDER)
	static class DefaultConfigurerAdapter extends WebSecurityConfigurerAdapter {
	}
}
```

> 1. 当前环境是`Servlet`，当存在`WebSecurityConfigurerAdapter`时，不注入`SpringBootWebSecurityConfiguration`，不存在时则注入默认的`DefaultConfigurerAdapter`。
> 2. 注入默认的`DefaultConfigurerAdapter`，同时指定`Order(Integer.MAX_VALUE - 5)`。

#### WebSecurityEnablerConfiguration

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(WebSecurityConfigurerAdapter.class)
@ConditionalOnMissingBean(name = BeanIds.SPRING_SECURITY_FILTER_CHAIN)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
public class WebSecurityEnablerConfiguration {
}
```

> 1. 当存在`WebSecurityConfigurerAdapter`、不存在`springSecurityFilterChain`且是`Servlet`环境时，激活`@EnableWebSecurity`注解。

##### @EnableWebSecurity

 ```java
@Retention(value = java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(value = { java.lang.annotation.ElementType.TYPE })
@Documented
@Import({ WebSecurityConfiguration.class,
		SpringWebMvcImportSelector.class,
		OAuth2ImportSelector.class })
@EnableGlobalAuthentication
@Configuration
public @interface EnableWebSecurity {
	boolean debug() default false;
}
 ```

> `@EnableWebSecurity`注解的核心在于引入`WebSecurityConfiguration`、`SpringWebMvcImportSelector`、`OAuth2ImportSelector`三个类。

- WebSecurityConfiguration

创建`Spring Security`相关的安全过滤器（beanId = `springSecurityFilterChain`）来对用户的请求进行过滤。

- SpringWebMvcImportSelector

当classpath下存在`DispatcherServlet`时注入`WebMvcSecurityConfiguration`类，主要是用于配置`SpringMVC`相关。

- OAuth2ImportSelector

当存在`ClientRegistration`时注入`OAuth2ClientConfiguration`，当存在`ExchangeFilterFunction`时注入`SecurityReactorContextConfiguration`，当存在`BearerTokenError`时注入`SecurityReactorContextConfiguration`。

- @EnableGlobalAuthentication

核心在于构建认证管理器`AuthenticationManager`。

##### SecurityDataConfiguration

自动添加Spring Security与Spring Data的集成。

### SecurityFilterAutoConfiguration

用于自动注入Spring Security的Filter过滤器类。

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnClass({ AbstractSecurityWebApplicationInitializer.class, SessionCreationPolicy.class })
@AutoConfigureAfter(SecurityAutoConfiguration.class)
public class SecurityFilterAutoConfiguration {
    // springSecurityFilterChain
    private static final 
        String DEFAULT_FILTER_NAME = AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME;

    /**
     * 当IOC容器中存在beanName为springSecurityFilterChain时注入DelegatingFilterProxyRegistrationBean
     */
	@Bean
	@ConditionalOnBean(name = DEFAULT_FILTER_NAME)
	public DelegatingFilterProxyRegistrationBean securityFilterChainRegistration(
			SecurityProperties securityProperties) {
		DelegatingFilterProxyRegistrationBean registration = new DelegatingFilterProxyRegistrationBean(
				DEFAULT_FILTER_NAME);
		registration.setOrder(securityProperties.getFilter().getOrder());
		registration.setDispatcherTypes(getDispatcherTypes(securityProperties));
		return registration;
	}
    // 省略
}
```

> 1. 与上文中的`SecurityAutoConfiguration`分开配置，是为了当存在用户指定的`WebSecurityConfiguration`时仍能指定Order顺序。
> 2. 在`SecurityFilterAutoConfiguration`完成后调用`SecurityAutoConfiguration`配置类。
> 3. IOC容器中存在BeanName为`springSecurityFilterChain`时注入`DelegatingFilterProxyRegistrationBean`，在上文的`@EnableWebSecurity`中的`WebSecurityConfiguration`引入。

#### DelegatingFilterProxyRegistrationBean

```java
public class DelegatingFilterProxyRegistrationBean extends AbstractFilterRegistrationBean<DelegatingFilterProxy>
		implements ApplicationContextAware {
    @Override
	public DelegatingFilterProxy getFilter() {
		return new DelegatingFilterProxy(this.targetBeanName, getWebApplicationContext()) {

			@Override
			protected void initFilterBean() throws ServletException {
				// Don't initialize filter bean on init()
			}

		};
	}
    // 省略
}
```

> 1. 通过`委派模式`将创建`ServletRegistrationBean`的委派类`DelegatingFilterProxyRegistrationBean`用于处理url和servlet的映射关系。
> 2. 将任务委派给名为`springSecurityFilterChain`的servlet代理类`DelegatingFilterProxy`来处理sevlet请求。
> 3. 实际处理servlet的是代理类`DelegatingFilterProxy`的实现类`FilterChainProxy`。

---

## 自定义配置

主要通过继承`WebSecurityConfigurerAdapter`抽象类来实现的。

```java
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	@Override
    protected void configure(HttpSecurity http) throws Exception {
        // 用于构建安全过滤器链
    }
	
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        // 处理用户认证相关（UserDetails）
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        // 配置webSecurity（基于DelegatingFilterProxy管理的springSecurityFilterChain实现）
    }
}
```

### HttpSecurity入门

```java
@Configuration
public class CommonSecurityConfig extends WebSecurityConfigurerAdapter {

    @Resource
    private UserManager userManager;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userManager).passwordEncoder(new BCryptPasswordEncoder());
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        super.configure(web);
    }

    private static final String LOGIN_PROCESS_URL = "/process";

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .cors()
                .and()
                .authorizeRequests().anyRequest().authenticated()
                .and()
                .addFilterBefore(new PreLoginFilter(LOGIN_PROCESS_URL, null), UsernamePasswordAuthenticationFilter.class) // 注入filter，进行登录提前校验
                .formLogin()
                .loginProcessingUrl(LOGIN_PROCESS_URL) // 实际向后台提交请求的路径，此后会执行UsernamePasswordAuthenticationFilter类
                // .defaultSuccessUrl("http://www.baidu.com", false) // login页面登录成功后重定向地址（如果是successfulForwardUrl则是转发）
                .successForwardUrl("/login/success")  // 登录成功后转发的路径（可以是接口）
                .failureForwardUrl("/login/failure");  // 登录失败的时候会转发到此路径

    }
}
```

> 1. 一般通过配置HttpSecurity来实现自定义登录或鉴权的配置。
> 2. 注入自定义Filter的核心原理在于登录鉴权相关的逻辑由`UsernamePasswordAuthenticationFilter`处理。

----

## 源码解析

### UsernamePasswordAuthenticationFilter

结合前文所示，用户的账户和密码认证是由`UsernamePasswordAuthenticationFilter`处理，所以我们以此切入。

```java
public class UsernamePasswordAuthenticationFilter extends
		AbstractAuthenticationProcessingFilter {

	public UsernamePasswordAuthenticationFilter() {
        // 处理/login的POST请求
		super(new AntPathRequestMatcher("/login", "POST"));
	}

    // 执行实际的认证流程
    public Authentication attemptAuthentication(HttpServletRequest request,
			HttpServletResponse response) throws AuthenticationException {
        // 只支持POST请求，对其进行校验
		if (postOnly && !request.getMethod().equals("POST")) {
			throw new AuthenticationServiceException(
					"Authentication method not supported: " + request.getMethod());
		}
        // 通过request.getParameter("username");获取用户名
		String username = obtainUsername(request);
        // 通过request.getParameter("password");获取用户密码
		String password = obtainPassword(request);
        // 判空及去重
		if (username == null) {
			username = "";
		}
		if (password == null) {
			password = "";
		}
		username = username.trim();
        // 将用户密码封装到UsernamePasswordAuthenticationToken中
		UsernamePasswordAuthenticationToken authRequest = new 		
            	UsernamePasswordAuthenticationToken(username, password);
		// 允许子类设置其他参数到认证请求中去
		setDetails(request, authRequest);
        // 调用AuthenticationManager去处理认证请求
		return this.getAuthenticationManager().authenticate(authRequest);
	}
}
```

> 该类的主要作用就是拦截request请求并获取账号和密码，再将其封装到`UsernamePasswordAuthenticationToken`中。再交给`AuthenticationManager`去认证。

### AbstractAuthenticationProcessingFilter

```java
public abstract class AbstractAuthenticationProcessingFilter extends GenericFilterBean
		implements ApplicationEventPublisherAware, MessageSourceAware {
    private AuthenticationSuccessHandler 
        successHandler = new SavedRequestAwareAuthenticationSuccessHandler(); // success处理器
	private AuthenticationFailureHandler 
        failureHandler = new SimpleUrlAuthenticationFailureHandler(); // failure处理器

    // 过滤器的核心方法
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;
		// 判断是否需要鉴权（本质就是判断路径是否匹配），由子类构造中实现的（POST /login请求）
		if (!requiresAuthentication(request, response)) {
			chain.doFilter(request, response);
			return;
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Request is to process authentication");
		}
		// 鉴权校验，实际上调用了子类的attemptAuthentication实现
		Authentication authResult;
		try {
            // 如果返回为空说明子类验证没有完成，立即返回
			authResult = attemptAuthentication(request, response);
			if (authResult == null) {
				return;
			}
            // 处理session策略，此处默认是空实现
			sessionStrategy.onAuthentication(authResult, request, response);
		}
		catch (InternalAuthenticationServiceException failed) {
			logger.error(
					"An internal error occurred while trying to authenticate the user.",
					failed);
            // 失败处理器处理
			unsuccessfulAuthentication(request, response, failed);
			return;
		}
		catch (AuthenticationException failed) {
			// 与上同理
			unsuccessfulAuthentication(request, response, failed);
			return;
		}
		// 是否跳过其他过滤器，默认是跳过的
		if (continueChainBeforeSuccessfulAuthentication) {
			chain.doFilter(request, response);
		}
        // 成功后的处理器处理
		successfulAuthentication(request, response, chain, authResult);
	}
    
}
```

> 1. 是`UsernamePasswordAuthenticationFilter`的父类，默认实现了`Filter`过滤器的核心方法`doFilter`。
> 2. 首先是对请求路径的判断，必须是`POST /login`请求才会拦截。否则直接交由下个过滤器处理。
> 3. 调用子类的`attemptAuthentication`进行认证操作，并设置session相关的策略（默认空实现）。
> 4. 如果发生了异常或校验失败，调用失败处理器。继而判断是否需要跳过后面的过滤器，最终执行成功处理器。



### AuthenticationManager

```java
public interface AuthenticationManager {
    Authentication authenticate(Authentication authentication)
			throws AuthenticationException;
}
```

> 认证管理器顶级接口，上文中封装的`UsernamePasswordAuthenticationToken`就会交予`AuthenticationManager`的实现类来处理。如果验证成功就返回`Authentication`对象，否则就抛出异常。

#### 初始化流程🔒

##### 1.  SecurityAutoConfiguration

>  请注意：下述的流程展示省略了大部分与`AuthenticationManager`初始化无关的代码！！

```java
// // 通过自动装配注入了`SecurityAutoConfiguration`类，继而注入了`WebSecurityEnablerConfiguration`
@Import({WebSecurityEnablerConfiguration.class})
public class SecurityAutoConfiguration {
}
```

##### 2. WebSecurityEnablerConfiguration

```java
@ConditionalOnBean(WebSecurityConfigurerAdapter.class)
@EnableWebSecurity
public class WebSecurityEnablerConfiguration {
}
```

> 因为容器中存在`WebSecurityConfigurerAdapter`，所以启用了`@EnableWebSecurity`注解。

##### 3. EnableWebSecurity🎈

```java
@Import({ WebSecurityConfiguration.class})
@EnableGlobalAuthentication
public @interface EnableWebSecurity {
}
```

> `@EnableWebSecurity`注解的核心在于`@EnableGlobalAuthentication`和`WebSecurityConfiguration`类。

- 1. @EnableGlobalAuthentication

```java
/**
 * 此注解可用于配置`AuthenticationManagerBuilder`实例，而`AuthenticationManagerBuilder`则
 * 用于创建`AuthenticationManager`实例
 */
@Import(AuthenticationConfiguration.class) // 注入AuthenticationConfiguration类
public @interface EnableGlobalAuthentication {
}

@Import(ObjectPostProcessorConfiguration.class) // 注入了ObjectPostProcessorConfiguration类
public class AuthenticationConfiguration {
}
```

> `@EnableGlobalAuthentication`的核心就是对`AuthenticationConfiguration`和`ObjectPostProcessorConfiguration`的注入。

- ObjectPostProcessorConfiguration

```java
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class ObjectPostProcessorConfiguration {

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public ObjectPostProcessor<Object> objectPostProcessor(
			AutowireCapableBeanFactory beanFactory) {
        // 创建默认的ObjectPostProcessor实现类注入到容器中
		return new AutowireBeanFactoryObjectPostProcessor(beanFactory);
	}
}

// 顶级接口
public interface ObjectPostProcessor<T> {
	<O extends T> O postProcess(O object);
}

final class AutowireBeanFactoryObjectPostProcessor
		implements ObjectPostProcessor<Object>, DisposableBean, SmartInitializingSingleton {
    
    // 将bean注入到容器的核心方法
    public <T> T postProcess(T object) {
		if (object == null) {
			return null;
		}
		T result = null;
		try {
            // 初始化bean
			result = (T) this.autowireBeanFactory
                			.initializeBean(object,object.toString());
		} catch (RuntimeException e) {
			// 省略
		}
        // 自动注入
		this.autowireBeanFactory.autowireBean(object);
		// 省略
		return result;
	}
}
```

> 1.  此类是通过`AuthenticationConfiguration`注入的，此处涉及一个概念：`ObjectPostProcessor`。
> 2. `ObjectPostProcessor`可以通过`new`创建的对象交由`IOC容器`进行管理。
> 3. `ObjectPostProcessorConfiguration`默认注入了`ObjectPostProcessor`的实现类`AutowireBeanFactoryObjectPostProcessor`到容器中。核心就是`初始化Bean`并自动注入。
> 4. 使用`ObjectPostProcessor`的目的是为了解决`因为便于管理大量对象，没有暴露这些对象的属性，但是需要手动注册bean到容器中`的问题，注入到容器中的bean我们可以对其进行管理、修改或增强。

- AuthenticationConfiguration🔒

```java
@Configuration(proxyBeanMethods = false)
@Import(ObjectPostProcessorConfiguration.class) // 上文已解析
public class AuthenticationConfiguration {
    // IOC容器上下文，BeanFactory的实现
    private ApplicationContext applicationContext;
    // 此处涉及authenticationManager注入
	private AuthenticationManager authenticationManager;
    // 默认false，用于判断authenticationManager是否已经初始化
	private boolean authenticationManagerInitialized;
    // 用于注入bean到容器中
    private ObjectPostProcessor<Object> objectPostProcessor; 
    
    @Bean
	public AuthenticationManagerBuilder authenticationManagerBuilder(
			ObjectPostProcessor<Object> objectPostProcessor, ApplicationContext context) {
        // 创建默认的解码器（上文有解析过，此处使用了静态工厂创建解码器）
		LazyPasswordEncoder defaultPasswordEncoder = new LazyPasswordEncoder(context);
		AuthenticationEventPublisher authenticationEventPublisher = 
            				getBeanOrNull(context, AuthenticationEventPublisher.class);
		// 创建默认的AuthenticationManagerBuilder，用于构建AuthenticationManager
        // 此处传入了上文的默认解码器，以及AutowireBeanFactoryObjectPostProcessor
		DefaultPasswordEncoderAuthenticationManagerBuilder result = 
            new DefaultPasswordEncoderAuthenticationManagerBuilder(
            								objectPostProcessor, defaultPasswordEncoder);
		if (authenticationEventPublisher != null) {
			result.authenticationEventPublisher(authenticationEventPublisher);
		}
		return result;
	}
    
    // WebSecurityConfigurerAdapter中通过AuthenticationConfiguration调用
    public AuthenticationManager getAuthenticationManager() throws Exception {
        // 如果已经初始化那么直接返回authenticationManager
		if (this.authenticationManagerInitialized) {
			return this.authenticationManager;
		}
        // 判断容器中是否存在AuthenticationManagerBuilder（AuthenticationManager的构造器）
		AuthenticationManagerBuilder authBuilder =
            this.applicationContext.getBean(AuthenticationManagerBuilder.class);
        // CAS保证线程安全，调用委派模式通过AuthenticationManagerBuilder创建AuthenticationManager
		if (this.buildingAuthenticationManager.getAndSet(true)) {
			return new AuthenticationManagerDelegator(authBuilder);
		}
		// 判断是否存在全局配置类（即继承GlobalAuthenticationConfigurerAdapter的类）
		for (GlobalAuthenticationConfigurerAdapter config : globalAuthConfigurers) {
			authBuilder.apply(config);
		}
		// 委派模式分配的类用于构建AuthenticationManager
		authenticationManager = authBuilder.build();
		// 若没有符合条件的委托类进行鉴权操作，那么就创建
		if (authenticationManager == null) {
            // 此处的authenticationManager还是可以为null的
			authenticationManager = getAuthenticationManagerBean();
		}
		// 标记为已创建AuthenticationManager并返回
		this.authenticationManagerInitialized = true;
		return authenticationManager;
	}
    
    static final class AuthenticationManagerDelegator implements AuthenticationManager {
		private AuthenticationManagerBuilder delegateBuilder;
		private AuthenticationManager delegate;
		private final Object delegateMonitor = new Object();

		AuthenticationManagerDelegator(AuthenticationManagerBuilder delegateBuilder) {
			Assert.notNull(delegateBuilder, "delegateBuilder cannot be null");
			this.delegateBuilder = delegateBuilder;
		}

		@Override
		public Authentication authenticate(Authentication authentication)
				throws AuthenticationException {
            // 如果AuthenticationManager不为null直接调用
			if (this.delegate != null) {
				return this.delegate.authenticate(authentication);
			}
			// 否则加锁并创建AuthenticationManager
			synchronized (this.delegateMonitor) {
				if (this.delegate == null) {
					this.delegate = this.delegateBuilder.getObject();
					this.delegateBuilder = null;
				}
			}
			// 最终调用AuthenticationManager.authenticate()
			return this.delegate.authenticate(authentication);
		}
    
    private AuthenticationManager getAuthenticationManagerBean() {
		return lazyBean(AuthenticationManager.class);
	}
    // 此步是用于创建AuthenticationManager并加入到容器中进行管理
    private <T> T lazyBean(Class<T> interfaceName) {
        // 获取BeanFactory的单例Bean
		LazyInitTargetSource lazyTargetSource = new LazyInitTargetSource();
        // 从容器中通过类型获取bean对象集合
		String[] beanNamesForType = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
				applicationContext, interfaceName);
		if (beanNamesForType.length == 0) {
			return null;
		}
		String beanName;
		if (beanNamesForType.length > 1) {
            // 存在相同类型的多个bean，判断是否有@Primary注解修饰的bean，若有则返回，否则报错
			List<String> primaryBeanNames = getPrimaryBeanNames(beanNamesForType);
			// 如果不存在或数量不等于1，就抛出异常
			Assert.isTrue(primaryBeanNames.size() != 0, () -> "Found " + beanNamesForType.length
					+ " beans for type " + interfaceName + ", but none marked as primary");
			Assert.isTrue(primaryBeanNames.size() == 1, () -> "Found " + primaryBeanNames.size()
					+ " beans for type " + interfaceName + " marked as primary");
			beanName = primaryBeanNames.get(0);
		} else {
            // 否则直接返回第一个
			beanName = beanNamesForType[0];
		}
		// 设置beanFactory相关参数
		lazyTargetSource.setTargetBeanName(beanName);
		lazyTargetSource.setBeanFactory(applicationContext);
        // 创建代理工厂并调用postProcess将new的对象加入容器中
		ProxyFactoryBean proxyFactory = new ProxyFactoryBean();
		proxyFactory = objectPostProcessor.postProcess(proxyFactory);
		proxyFactory.setTargetSource(lazyTargetSource);
        // 返回容器中的符合条件的对象(即AuthenticationManager对象)
		return (T) proxyFactory.getObject();
	}
}
```

> 1. `AuthenticationConfiguration`提供了默认解码器和基于默认解码器的鉴权管理构造器。
> 2. 提供了`getAuthenticationManager()`用于返回容器中的`AuthenticationManager`对象。
> 3. 尝试通过获取容器中的`AuthenticationManagerBuilder`并调用委派模式、建造者模式来创建`AuthenticationManager`。
> 4. 如果仍没有，么会基于类型在容器中进行查找（找不到或多个会抛出异常），然后进行鉴权，如果成功返回`Authentication`，否则抛出异常。

- 2. WebSecurityConfiguration

```java
@Configuration(proxyBeanMethods = false)
public class WebSecurityConfiguration implements ImportAware, BeanClassLoaderAware {
    
    private WebSecurity webSecurity;
    private List<SecurityConfigurer<Filter, WebSecurity>> webSecurityConfigurers;
    
    @Autowired(required = false)
	public void setFilterChainProxySecurityConfigurer(){ 
        // 此处代码是把容器中的SecurityConfigurer的实现类转换为SecurityBuilder设置为webSecurity的属性
        // 并将SecurityConfigurer的实现类加入集合中
    }
    
    // 创建beanName为'springSecurityFilterChain'的过滤器链并得到整合后的Filter
    @Bean(name = AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME)
	public Filter springSecurityFilterChain() throws Exception {
        // webSecurityConfigurers 是用于创建web配置的对象集合
		boolean hasConfigurers = webSecurityConfigurers != null
				&& !webSecurityConfigurers.isEmpty();
		if (!hasConfigurers) {
            // 若没有SecurityConfigurer的实现类（只要继承了WebSecurityConfigurerAdapter就不会为空)
            // 则创建默认的WebSecurityConfigurerAdapter类
			WebSecurityConfigurerAdapter adapter = objectObjectPostProcessor
					.postProcess(new WebSecurityConfigurerAdapter() {
					});
			webSecurity.apply(adapter);
		}
        // 将WebSecurity对象转换为Filter（查看下文）
		return webSecurity.build();
	}
}
```

> 1. 基于`WebSecurityConfiguration`来创建`WebSecurity`对象。
> 2. `WebSecurity`处理`Filter过滤器链`相关，`HttpSecurity`处理http请求相关，都实现自`SecurityBuilder`。
> 3. 如果容器中`SecurityConfigurer<Filter, WebSecurity>`的子类、实现类集合为空，那么就会创建默认的`WebSecurityConfigurerAdapter`对象并加入到容器中。

- AbstractSecurityBuilder.build()🔒

```java
public abstract class AbstractSecurityBuilder<O> implements SecurityBuilder<O> {
    
    private AtomicBoolean building = new AtomicBoolean();
	private O object;
    
    public final O build() throws Exception {
        // CAS保证多线程下只能创建一次
		if (this.building.compareAndSet(false, true)) {
			this.object = doBuild();
			return this.object;
		}
		throw new AlreadyBuiltException("This object has already been built");
	}
    // 模板方法,由子类具体实现
    protected abstract O doBuild() throws Exception;
}

public abstract class AbstractConfiguredSecurityBuilder<O, B extends SecurityBuilder<O>>
		extends AbstractSecurityBuilder<O> {
    @Override
	protected final O doBuild() throws Exception {
        // 加锁初始化,BuildState由五种状态
		synchronized (configurers) {
			buildState = BuildState.INITIALIZING;
			beforeInit(); // 钩子函数,初始化前调用,默认空实现
			init();
			buildState = BuildState.CONFIGURING;
			beforeConfigure(); // 钩子函数,配置前调用,默认空实现
			configure();
			buildState = BuildState.BUILDING;
			O result = performBuild();
			buildState = BuildState.BUILT;
			return result;
		}
	}
    private void init() throws Exception {
        // 获取所有security的配置类
		Collection<SecurityConfigurer<O, B>> configurers = getConfigurers();
		// 依次初始化他们
		for (SecurityConfigurer<O, B> configurer : configurers) {
			configurer.init((B) this); // 此处会调用`WebSecurityConfigurerAdapter.init()方法`
		}
		// 所有调用apply的security的配置类都会加入其中,用于立即调用
		for (SecurityConfigurer<O, B> configurer : configurersAddedInInitializing) {
			configurer.init((B) this);
		}
	}
}
```

> 1. 



#### WebSecurityConfigurerAdapter

```java
public abstract class WebSecurityConfigurerAdapter implements
		WebSecurityConfigurer<WebSecurity> {
    private boolean disableLocalConfigureAuthenticationBldr;
    private AuthenticationManager authenticationManager;
    
    // 核心：获取AuthenticationManager来使用
    protected AuthenticationManager authenticationManager() throws Exception {
        // AuthenticationManager是否已经初始化
		if (!authenticationManagerInitialized) {
            
			configure(localConfigureAuthenticationBldr);
			if (disableLocalConfigureAuthenticationBldr) {
				authenticationManager = authenticationConfiguration
						.getAuthenticationManager();
			}
			else {
				authenticationManager = localConfigureAuthenticationBldr.build();
			}
			authenticationManagerInitialized = true;
		}
		return authenticationManager;
	}
    
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		this.disableLocalConfigureAuthenticationBldr = true;
	}
}
```

> 只要注入`WebSecurityConfigurerAdapter`或其子类，那么就会执行初始化（`@SecurityAutoConfiguration`注解会注入默认的`DefaultConfigurerAdapter`当classpath下不存在时）。

