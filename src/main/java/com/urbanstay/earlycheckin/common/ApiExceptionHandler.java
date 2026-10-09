package com.urbanstay.earlycheckin.common;

import java.util.NoSuchElementException;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 요청 값이 잘못되면 400, 대상이 없으면 404, 현재 상태에서 할 수 없는 동작이면 409. 화면은 detail을 그대로 보여준다. */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail badRequest(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(NoSuchElementException.class)
	ProblemDetail notFound(NoSuchElementException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	ProblemDetail conflict(IllegalStateException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	/** 같은 예약을 동시에 바꾼 요청 (예: 결제 버튼 동시 클릭) 중 늦은 쪽 */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail concurrentUpdate(OptimisticLockingFailureException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "다른 요청이 먼저 처리되었습니다. 화면을 새로 고쳐 주세요.");
	}

}
