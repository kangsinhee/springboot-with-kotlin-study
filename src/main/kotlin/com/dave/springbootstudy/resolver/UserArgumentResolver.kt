package com.dave.springbootstudy.resolver

import com.dave.springbootstudy.annotation.SocialUser
import com.dave.springbootstudy.domain.User
import com.dave.springbootstudy.domain.enums.SocialType
import com.dave.springbootstudy.domain.enums.SocialType.FACEBOOK
import com.dave.springbootstudy.domain.enums.SocialType.GOOGLE
import com.dave.springbootstudy.domain.enums.SocialType.KAKAO
import com.dave.springbootstudy.domain.enums.SocialType.NAVER
import com.dave.springbootstudy.repository.UserRepository
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.provider.OAuth2Authentication
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.Instant
import javax.servlet.http.HttpSession

@Component
class UserArgumentResolver(private val userRepository: UserRepository): HandlerMethodArgumentResolver {
	// HandlerMethodArgumentResolver가 해당하는 파라미터를 지원할지 여부 반환. true이면 resolveArgument가 실행됨.
	override fun supportsParameter(parameter: MethodParameter?): Boolean =
		// @SocialUser 어노테이션이 있고 타입이 User인 파라미터만 true로 반환
		parameter?.getParameterAnnotation(SocialUser::class.java) != null &&
			parameter.parameterType == User::class

	// 파라미터 인자값에 대한 정보를 바탕으로 실제 객체를 생성하여 해당 파라미터 객체에 바인딩.
	override fun resolveArgument(
		parameter: MethodParameter?,
		mavContainer: ModelAndViewContainer?,
		webRequest: NativeWebRequest?,
		binderFactory: WebDataBinderFactory?
	): Any? {
		val session = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes)
			.request
			.session
		val user = session.getAttribute("user") as User?
		return getUser(user, session)
	}

	private fun getUser(user: User?, session: HttpSession?): Any? {
		if (user == null) {
			try {
				val authentication = SecurityContextHolder.getContext().authentication as OAuth2Authentication
				// 리소스에서 받은 정보를 details를 사용해 map 타입으로 받을 수 있다.
				val map = authentication.userAuthentication.details as Map<String, String>
				val convertUser = convertUser(authentication.authorities.toTypedArray().first().toString(), map)

				user = userRepository.findByEmail(convertUser!!.email)
				
			}
		}
	}

	private fun convertUser(authority: String, map: Map<String, String>): User? {
		return when(authority) {
			FACEBOOK.name -> getModernUser(FACEBOOK, map)
			GOOGLE.name -> getModernUser(FACEBOOK, map)
			KAKAO.name -> getKakaoUser(map)
			NAVER.name -> getModernUser(NAVER, map)
			else -> null
		}
	}

	private fun getModernUser(socialType: SocialType, map: Map<String, String>): User {
		return User(
			name = map["name"]!!,
			email = map["email"]!!,
			principal = map["id"]!!,
			socialType = socialType
		)
	}

	private fun getKakaoUser(map: Map<String, String>): User {
		val propertyMap = map["properties"] as (Any) as Map<String, String>
		return User(
			name = propertyMap["nickname"]!!,
			email = map["kaccount_email"]!!,
			principal = map["id"]!!,
			socialType = KAKAO
		)
	}

	private fun setRoleIfNotSame(
		user: User,
		authentication: OAuth2Authentication,
		map: Map<String, String>
	) {
		if(!authentication.authorities.contains(SimpleGrantedAuthority(user.socialType.roleType))) {
			SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
				map,
				"N/A",
				AuthorityUtils.createAuthorityList(user.socialType.roleType)
			)
		}
	}
}