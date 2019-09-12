package it.msec.kio

import it.msec.kio.EvalFn.eager
import it.msec.kio.EvalFn.evalAccessEnv
import it.msec.kio.EvalFn.evalFlatMap
import it.msec.kio.EvalFn.evalMap
import it.msec.kio.EvalFn.execute
import it.msec.kio.EvalFn.lazy

typealias KIO<R, E, A> = Eval<R, Result<E, A>>
typealias BIO<E, A> = KIO<Any, E, A>
typealias EnvTask<R, A> = Eval<R, Success<A>>
typealias Task<A> = EnvTask<Any, A>

fun <A> task(f: suspend () -> A): Task<A> = lazy { Success(f()) }

fun <R, A> taskEnv(f: suspend () -> A): EnvTask<R, A> = lazy { Success(f()) }

fun <A> just(v: A): Task<A> = eager(Success(v))

fun <R, A> justEnv(v: A): EnvTask<R, A> = eager(Success(v))

fun <E> failure(e: E): BIO<E, Nothing> = eager(Failure(e))

inline fun <A> unsafe(crossinline f: suspend () -> A): KIO<Any, Throwable, A> = unsafeEnv(f)

inline fun <R, A> unsafeEnv(crossinline f: suspend () -> A): KIO<R, Throwable, A> = lazy {
    try {
        Success(f())
    } catch (t: Throwable) {
        Failure(t)
    }
}

inline fun <R, E, A, B> KIO<R, E, A>.map(crossinline f: (A) -> B): KIO<R, E, B> = evalMap {
    when (it) {
        is Success -> Success(f(it.value))
        is Failure -> it
    }
}

inline fun <R, E, A, B> KIO<R, E, A>.flatMap(crossinline f: suspend (A) -> KIO<R, E, B>): KIO<R, E, B> = evalFlatMap {
    when (it) {
        is Success -> f(it.value)
        is Failure -> eager(it)
    }
}

inline fun <R, E, A, B> KIO<R, E, A>.flatMapEnv(crossinline f: suspend (R) -> KIO<R, E, B>): KIO<R, E, B> =
        evalAccessEnv { f(it) }

fun <R, E, A, B> KIO<R, E, A>.mapEnv(f: suspend (R) -> B): KIO<R, E, B> =
        evalAccessEnv { justEnv(f(it)) }

inline fun <R, E, L, A> KIO<R, E, A>.mapError(crossinline f: (E) -> L): KIO<R, L, A> = evalMap {
    when (it) {
        is Success -> it
        is Failure -> Failure(f(it.error))
    }
}

fun <R, E, A> KIO<R, E, A>.swap(): KIO<R, A, E> = evalMap {
    when (it) {
        is Success -> Failure(it.value)
        is Failure -> Success(it.error)
    }
}

fun <R, A> EnvTask<R, A>.attempt(): KIO<R, Throwable, A> =
        evalAccessEnv { env -> unsafeEnv { this@attempt.execute(env).value } }


inline fun <R, E, A> KIO<R, E, A>.recover(crossinline f: (E) -> A): EnvTask<R, A> = evalFlatMap {
    when (it) {
        is Success -> eager<R, Success<A>>(it)
        is Failure -> taskEnv<R, A> { f(it.error) }
    }
}

inline fun <R, E, A> KIO<R, E, A>.tryRecover(crossinline f: suspend (E) -> KIO<R, E, A>): KIO<R, E, A> = evalFlatMap {
    when (it) {
        is Success -> eager(it)
        is Failure -> f(it.error)
    }
}

fun <R, E, A, C> KIO<R, E, A>.fold(e: (E) -> C, f: (A) -> C): EnvTask<R, C> =
    map(f).recover(e)

inline fun <R, E, A, B> KIO<R, E, A>.flatMapT2(crossinline f: (A) -> KIO<R, E, B>): KIO<R, E, T2<A,B>> = evalFlatMap {
    when (it) {
        is Success -> f(it.value).map { v -> T2(it.value, v) }
        is Failure -> eager(it)
    }
}

inline fun <R, E, A, B, C> KIO<R, E, T2<A, B>>.flatMapT3(crossinline f: (T2<A, B>) -> KIO<R, E, C>): KIO<R, E, T3<A,B, C>> = evalFlatMap {
    when (it) {
        is Success -> f(it.value).map { v -> T3(it.value._1, it.value._2, v) }
        is Failure -> eager(it)
    }
}

inline fun <R, E, A, B> KIO<R, E, A>.mapT2(crossinline f: (A) -> B): KIO<R, E, T2<A,B>> = evalMap {
    when (it) {
        is Success -> Success(T2(it.value, f(it.value)))
        is Failure -> it
    }
}

inline fun <R, E, A, B, C> KIO<R, E, T2<A, B>>.mapT3(crossinline f: (T2<A, B>) -> C): KIO<R, E, T3<A,B, C>> = evalMap {
    when (it) {
        is Success -> Success(T3(it.value._1, it.value._2, f(it.value)))
        is Failure -> it
    }
}

