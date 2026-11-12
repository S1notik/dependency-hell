FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /build
COPY Resolver.csproj Program.cs VersionResolver.cs SelfChecks.cs ./
RUN dotnet publish Resolver.csproj -c Release -o /app && dotnet /app/Resolver.dll --self-test

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app ./
EXPOSE 8083
ENTRYPOINT ["dotnet", "Resolver.dll"]
