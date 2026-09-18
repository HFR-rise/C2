package com.example.myapplication.services

sealed class ShareException(message: String) : Exception(message) {
    class UserNotFound : ShareException("Пользователь с таким номером не найден")
    class InvalidPhone : ShareException("Неверный номер телефона")
    class NoPermission : ShareException("Нет прав для расшаривания этой сметы")
    class Unknown(val code: Int) : ShareException("Ошибка сервера: $code")
}